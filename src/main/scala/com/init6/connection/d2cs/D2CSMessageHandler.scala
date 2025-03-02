package com.init6.connection.d2cs

import akka.actor.{ActorRef, FSM, Props}
import akka.util.ByteString
import com.init6.coders.d2cs.packets.{D2CSAccountLoginRequest, D2CSAuthReply, D2CSAuthRequest, D2CSCharLoginRequest, D2CSGameInfoRequest, Packets}
import com.init6.connection.{ConnectionInfo, Init6KeepAliveActor, WriteOut}

import scala.collection.mutable



sealed trait D2CSState
case object Always extends D2CSState

case class D2CSPacket(packetId: Byte, seqno: Int, packet: ByteString)

object D2CSMessageHandler {
  private var nextSessionNum = 1
  private val sessionCache = mutable.HashMap[Int, String]()

  def apply(connectionInfo: ConnectionInfo): Props = Props(classOf[D2CSMessageHandler], connectionInfo)
}

class D2CSMessageHandler(connectionInfo: ConnectionInfo) extends Init6KeepAliveActor with FSM[D2CSState, ActorRef] {

  val selfSessionnum: Int = D2CSMessageHandler.nextSessionNum

  startWith(Always, ActorRef.noSender)
  context.watch(connectionInfo.actor)

  send(D2CSAuthRequest(selfSessionnum))
  D2CSMessageHandler.nextSessionNum += 1

  when (Always) {
    case Event(D2CSPacket(id, seqno, data), _) =>
      id match {
        case Packets.D2CS_AUTHREPLY =>
          data match {
            case D2CSAuthReply(packet) =>
              log.info(">> Received D2CS_AUTHREPLY")
              send(D2CSAuthReply(D2CSAuthReply.RESULT_SUCCESS))
          }
        case Packets.D2CS_ACCOUNTLOGINREQ =>
          data match {
            case D2CSAccountLoginRequest(packet) =>
              log.info(">> Received D2CS_ACCOUNTLOGINREQ")
          }
        case Packets.D2CS_CHARLOGINREQ =>
          data match {
            case D2CSCharLoginRequest(packet) =>
              log.info(">> Received D2CS_CHARLOGINREQ")
          }
        case Packets.D2CS_GAMEINFOREQ =>
          data match {
            case D2CSGameInfoRequest(packet) =>
              log.info(">> Received D2CS_GAMEINFOREQ")
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