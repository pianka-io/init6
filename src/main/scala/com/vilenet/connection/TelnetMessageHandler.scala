package com.vilenet.connection

import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, FSM, Props}
import akka.io.Tcp.{Close, Received}
import akka.pattern.ask
import akka.util.{Timeout, ByteString}
import com.vilenet.Constants._
import com.vilenet.coders.binary.hash.BSHA1
import com.vilenet.db.DAO
import com.vilenet.{ViLeNetActor, ViLeNetClusterActor, Config}
import com.vilenet.channels._
import com.vilenet.coders.telnet.TelnetEncoder
import com.vilenet.users.{UsersUserAdded, TelnetProtocol, Add}

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Await

sealed trait State
case object ExpectingUsername extends State
case object ExpectingPassword extends State
case object LoggedIn extends State
case object Blocked extends State

sealed trait Data
case object UnauthenticatedUser extends Data
case class UnauthenticatedUser(user: String) extends Data
case class AuthenticatedUser(user: User, actor: ActorRef) extends Data

case object JustLoggedIn extends ChatEvent

/**
 * Created by filip on 9/19/15.
 */
object TelnetMessageReceiver {
  def apply(clientAddress: InetSocketAddress, connection: ActorRef) =
    Props(classOf[TelnetMessageReceiver], clientAddress, connection)
}

class TelnetMessageReceiver(clientAddress: InetSocketAddress, connection: ActorRef) extends ViLeNetActor {

  val handler = context.actorOf(TelnetMessageHandler(clientAddress, connection))
  val buffer = ArrayBuffer[Byte]()


  override def receive: Receive = {
    case Received(data) =>
      ////println(data.utf8String.replace('\r','~').replace('\n','|'))
      val readData = data.takeWhile(b => b != '\r' && b != '\n')
      if (data.length == readData.length) {
        // Split packet
        buffer ++= readData
      } else {
        // End of packet found
        if (buffer.nonEmpty) {
          handler ! Received(ByteString(buffer.toArray[Byte] ++ readData.toArray[Byte]))
          buffer.clear()
        } else if (readData.nonEmpty) {
          handler ! Received(readData)
        }
      }
      val restOfData = data.drop(readData.length).dropWhile(b => b == '\r' || b == '\n')
      if (restOfData.nonEmpty) {
        receive(Received(restOfData))
      }
    case x =>
      //println(s"Received $x and closing handler.")
      connection ! Close
  }
}

object TelnetMessageHandler {
  def apply(clientAddress: InetSocketAddress, connection: ActorRef) =
    Props(classOf[TelnetMessageHandler], clientAddress, connection)
}

class TelnetMessageHandler(clientAddress: InetSocketAddress, connection: ActorRef) extends ViLeNetKeepAliveActor with FSM[State, Data] {

  implicit val timeout = Timeout(1, TimeUnit.MINUTES)

  startWith(ExpectingUsername, UnauthenticatedUser)
  context.watch(connection)

  def sendNull() = {
    connection ! WriteOut(TelnetEncoder(UserNull).get)
  }

  when (ExpectingUsername) {
    case Event(Received(data), _) =>
      goto(ExpectingPassword) using UnauthenticatedUser(data.utf8String)
  }

  when (ExpectingPassword) {
    case Event(Received(data), buffer: UnauthenticatedUser) =>
      DAO.getUser(buffer.user).fold({
        connection ! WriteOut(TelnetEncoder(TELNET_INCORRECT_USERNAME))
        goto(Blocked)
      })(dbUser => {
        if (dbUser.closed) {
          connection ! WriteOut(TelnetEncoder(ACCOUNT_CLOSED(buffer.user, dbUser.closedReason)))
          goto(Blocked)
        } else {
          if (BSHA1(data.toArray).sameElements(dbUser.passwordHash)) {
            val u = User(buffer.user, dbUser.flags | Flags.UDP, 0, client = "TAHC")
            Await.result(usersActor ? Add(connection, u, TelnetProtocol), timeout.duration) match {
              case UsersUserAdded(actor, user) => goto(LoggedIn) using AuthenticatedUser(user, actor)
              case x => stop()
            }
          } else {
            connection ! WriteOut(TelnetEncoder(TELNET_INCORRECT_PASSWORD))
            goto(Blocked)
          }
        }
      })
  }

  when (LoggedIn) {
    case Event(JustLoggedIn, buffer: AuthenticatedUser) =>
      connection ! WriteOut(TelnetEncoder(TELNET_CONNECTED(clientAddress)))
      connection ! WriteOut(TelnetEncoder(UserName(buffer.user.name)).get)
      connection ! WriteOut(TelnetEncoder(UserInfoArray(Config.motd)).get)
      //keepAlive(buffer.actor, sendNull)
      stay()
    case Event(Received(data), buffer: AuthenticatedUser) =>
      keptAlive = 0
      buffer.actor ! Received(data)
      stay()
  }

  when (Blocked) {
    case _ => stay()
  }

  whenUnhandled {
    case x =>
      //log.error(s"Unhandled Event $x")
      stop()
  }

  onTransition {
    case ExpectingPassword -> LoggedIn =>
      self ! JustLoggedIn
  }

  onTermination {
    case x =>
      //log.error(s"Connection stopped $x")
      context.stop(self)
  }
}
