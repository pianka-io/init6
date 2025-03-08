package com.init6.connection.d2cs

import akka.actor.{ActorRef, FSM, Props}
import akka.util.ByteString
import com.init6.coders.d2cs.packets.{D2CSAccountLoginRequest, D2CSAuthReply, D2CSAuthRequest, D2CSCharLoginRequest, D2CSGameInfoRequest, Packets}
import com.init6.connection.d2cs.D2CSMessageHandler.{gameCache, userCache}
import com.init6.connection.{ConnectionInfo, Init6KeepAliveActor, WriteOut}
import com.init6.users.SetCharacter
import com.init6.utils.HttpUtils

import scala.collection.mutable

sealed trait D2CSState
case object Always extends D2CSState

sealed trait D2CSCommand
case class D2CSGameRequest(difficulty: Byte, name: String) extends D2CSCommand
case class D2CSGameResponse(successful: Boolean) extends D2CSCommand

case class D2CSPacket(packetId: Byte, seqno: Int, packet: ByteString)

object D2CSMessageHandler {
  var actor: Option[ActorRef] = None
  private var nextSessionNum = 1
  private val userCache = mutable.HashMap[Int, String]()
  private val gameCache = mutable.HashMap[String, ActorRef]()

  def apply(connectionInfo: ConnectionInfo): Props = Props(classOf[D2CSMessageHandler], connectionInfo)
}

class D2CSMessageHandler(connectionInfo: ConnectionInfo) extends Init6KeepAliveActor with FSM[D2CSState, ActorRef] {

  D2CSMessageHandler.actor = Some(self)

  val selfSessionnum: Int = D2CSMessageHandler.nextSessionNum
  D2CSMessageHandler.nextSessionNum += 1
  send(D2CSAuthRequest(selfSessionnum))
  log.info(">> Sent D2CS_AUTHREQ")

  startWith(Always, ActorRef.noSender)
  context.watch(connectionInfo.actor)

  when (Always) {
    case Event(D2CSGameRequest(difficulty, name), _) =>
      log.info(">> Received D2CSGameRequest")
      D2CSMessageHandler.gameCache += name -> sender()
      send(D2CSGameInfoRequest(name))
      log.info(">> Sent D2CS_GAMEINFOREQ")
      stay()
    case Event(D2CSPacket(id, seqno, data), _) =>
      id match {
        case Packets.D2CS_AUTHREPLY =>
          data match {
            case D2CSAuthReply(packet) =>
              log.info(">> Received D2CS_AUTHREPLY")
              send(D2CSAuthReply(seqno, D2CSAuthReply.RESULT_SUCCESS))
              log.info(">> Sent D2CS_AUTHREPLY")
          }
        case Packets.D2CS_ACCOUNTLOGINREQ =>
          data match {
            case D2CSAccountLoginRequest(packet) =>
              log.info(">> Received D2CS_ACCOUNTLOGINREQ")
              userCache += packet.sessionnum -> packet.accountName
              send(D2CSAccountLoginRequest(seqno, D2CSAuthReply.RESULT_SUCCESS))
              val message = s"**${packet.accountName}** signed into **Sanctuary**."
              HttpUtils.postMessage("http://127.0.0.1:8080/d2_activity", message)
              log.info(">> Sent D2CS_ACCOUNTLOGINREQ")
          }
        case Packets.D2CS_CHARLOGINREQ =>
          data match {
            case D2CSCharLoginRequest(packet) =>
              log.info(">> Received D2CS_CHARLOGINREQ")
              userCache.get(packet.sessionnum).foreach(u => {
                usersActor ! SetCharacter(u, packet.characterName, packet.characterPortrait)
              })
              val message = s"**${userCache.get(packet.sessionnum)}** logged onto **${packet.characterName}**."
              HttpUtils.postMessage("http://127.0.0.1:8080/d2_activity", message)
              send(D2CSCharLoginRequest(seqno, D2CSAuthReply.RESULT_SUCCESS))
              log.info(">> Sent D2CS_CHARLOGINREQ")
          }
        case Packets.D2CS_GAMEINFOREQ =>
          log.info("[D2CSMessageHandler] D2CS_GAMEINFOREQ")
          data match {
            case D2CSGameInfoRequest(packet) =>
              log.info(">> Received D2CS_GAMEINFOREPLY")
              gameCache.get(packet.gameName).foreach(a => {
                a ! D2CSGameResponse(true)
                log.info(">> Sent D2CSGameResponse")
              })
            case a =>
              log.info(">> Unhandled {}", a.toString())
          }
        case _ =>
          log.info("[D2CSMessageHandler] Unhandled 0x{}", f"$id%X")
      }
      stay()
    case a =>
      log.info("[D2CSMessageHandler] Unhandled {}", a.toString)
      stay()
  }

  def send(data: ByteString): Unit = {
    connectionInfo.actor ! WriteOut(data)
  }
}
