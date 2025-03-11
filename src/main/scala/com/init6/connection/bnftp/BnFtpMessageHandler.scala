package com.init6.connection.bnftp

import java.util.concurrent.TimeUnit
import akka.actor.{ActorRef, FSM, PoisonPill, Props}
import akka.io.Tcp.Received
import akka.util.{ByteString, Timeout}
import com.init6.Config
import com.init6.Constants._
import com.init6.channels.{User, UserInfoArray}
import com.init6.coders.binary.BinaryChatEncoder
import com.init6.coders.binary.hash.BSHA1
import com.init6.coders.binary.packets.Packets._
import com.init6.coders.binary.packets._
import com.init6.coders.bnftp.BnFtpV1
import com.init6.coders.commands.{FriendsList, PongCommand}
import com.init6.connection._
import com.init6.db.{CreateAccount, DAO, DAOCreatedAck, UpdateAccountPassword}
import com.init6.users._
import com.init6.utils.LimitedAction

import java.io.File
import scala.util.Random

sealed trait BnFtpState
case object RequestState extends BnFtpState

case class BnFtpPacket(version: Short, packet: ByteString)


object BnFtpMessageHandler {
  def apply(connectionInfo: ConnectionInfo) = Props(classOf[BnFtpMessageHandler], connectionInfo)
}

class BnFtpMessageHandler(connectionInfo: ConnectionInfo) extends Init6KeepAliveActor with FSM[BnFtpState, ActorRef] {

  startWith(RequestState, ActorRef.noSender)
  context.watch(connectionInfo.actor)

  def send(data: ByteString) = {
    connectionInfo.actor ! WriteOut(data)
  }

  when(RequestState) {
    case Event(BnFtpPacket(version, data), _) =>
      data match {
        case BnFtpV1(packet) =>
          log.debug("<< {} {}", connectionInfo.actor, f"Sending ${packet.fileName}")
          send(BnFtpV1(packet.fileStartPosition, new File("src/main/resources/bnftp/" + packet.fileName)))
      }
      stay()
    case x =>
      log.debug(">> received unknown bnftp event {}", x.toString)
      stay()
  }

  onTermination {
    case x =>
      log.debug(">> {} BinaryMessageHandler onTermination: {}", connectionInfo.actor, x)
      connectionInfo.actor ! PoisonPill
  }
}
