package com.init6.connection.d2cs

import akka.actor.{ActorRef, FSM, PoisonPill, Props, Terminated}
import akka.util.ByteString
import com.init6.Config
import com.init6.coders.d2cs.packets.{D2CSAccountLoginRequest, D2CSAuthReply, D2CSAuthRequest, D2CSCharLoginRequest, D2CSGameInfoRequest, Packets}
import com.init6.connection.{ConnectionInfo, Init6KeepAliveActor, WriteOut}
import com.init6.users.SetCharacter
import com.init6.utils.HttpUtils

import scala.collection.mutable

sealed trait D2CSState
case object Always extends D2CSState

case class D2CSPacket(packetId: Byte, seqno: Int, packet: ByteString)

object D2CSMessageHandler {
  def apply(connectionInfo: ConnectionInfo): Props = Props(classOf[D2CSMessageHandler], connectionInfo)
}

class D2CSMessageHandler(connectionInfo: ConnectionInfo) extends Init6KeepAliveActor with FSM[D2CSState, ActorRef] {

  private val hostString = connectionInfo.ipAddress.getHostString

  log.info(s"Created D2CS Message Handler - IP: ${hostString}")
  d2csActor ! RegisterRealm(self, connectionInfo) //Add check to see if realm is in the config.
  //Does D2CS go through iplimitactor? Could it get ipbanned?

  startWith(Always, ActorRef.noSender)
  context.watch(connectionInfo.actor)

  when (Always) {
    case Event(D2CSRegistered(connectionInfo, sessionNum), _) =>
      send(D2CSAuthRequest(sessionNum))
      log.info(s"Realm Registered received from D2CSActor, connectionInfo: ${connectionInfo.actor}, sessionNum: ${sessionNum}")
      log.info(s"[${hostString}] >> Sent D2CS_AUTHREQ(Session #: ${sessionNum})")
      stay()
    case Event(ReturnAccountLoginRequest(seqNum, accountName, result), _) =>
      result match {
        case D2CSAccountLoginRequest.RESULT_SUCCESS =>
          val realmName = Config().Realm.realms.find(_._2 == connectionInfo.ipAddress.getHostString)
            .map { case (realmName, _, _) => realmName } //Check this?
            .getOrElse(("Unknown"))
          val message = s"**${accountName}** signed into **${realmName}**."
          //HttpUtils.postMessage("http://127.0.0.1:8889/d2_activity", message)
          send(D2CSAccountLoginRequest(seqNum, D2CSAccountLoginRequest.RESULT_SUCCESS))
          log.info(s"[${hostString}] >> Sent D2CS_ACCOUNTLOGINREQ(Sequence #: ${seqNum}, Result: ${D2CSAccountLoginRequest.RESULT_SUCCESS})")
        case D2CSAccountLoginRequest.RESULT_FAILURE =>
          send(D2CSAccountLoginRequest(seqNum, D2CSAccountLoginRequest.RESULT_FAILURE))
          log.info(s"[${hostString}] >> Sent D2CS_ACCOUNTLOGINREQ(Sequence #: ${seqNum}, Result: ${D2CSAccountLoginRequest.RESULT_FAILURE})")
      }
      stay()
    case Event(ReturnCharLoginRequest(seqNum, clientId, accountName, characterName, result), _) =>
      result match {
        case D2CSCharLoginRequest.RESULT_SUCCESS =>
          val message = s"**${accountName}** logged onto **${characterName}**."
          //HttpUtils.postMessage("http://127.0.0.1:8889/d2_activity", message)
          log.info(s"[${hostString}] >>  Sent D2CS_CHARLOGINREQ(Sequence #: ${seqNum}, Result: ${D2CSCharLoginRequest.RESULT_SUCCESS}")
          send(D2CSCharLoginRequest(seqNum, D2CSCharLoginRequest.RESULT_SUCCESS))
        case D2CSCharLoginRequest.RESULT_FAILURE =>
          log.info(s"[${hostString}] >>  Sent D2CS_CHARLOGINREQ(Sequence #: ${seqNum}, Result: ${D2CSCharLoginRequest.RESULT_FAILURE}")
          send(D2CSCharLoginRequest(seqNum, D2CSCharLoginRequest.RESULT_FAILURE))
      }
      log.info(s">> Sent D2CS_CHARLOGINREQ(${result})")
      stay()
    case Event(GameInfoRequest(connectionInfo, gameName), _) =>
      send(D2CSGameInfoRequest(gameName))
      log.info(s">> Send D2CSGameInfoRequest(Game Name: ${gameName})")
      stay()
    case Event(D2CSPacket(id, seqno, data), _) =>
      id match {
        case Packets.D2CS_AUTHREPLY =>
          data match {
            case D2CSAuthReply(packet) =>
              log.info(s"[${hostString}] >> Received D2CS_AUTHREPLY Sequence # ${seqno} for ${packet.realmName}")
              d2csActor ! SetRealmName(connectionInfo, packet.realmName)
              //realmName = packet.realmName
              send(D2CSAuthReply(seqno, D2CSAuthReply.RESULT_SUCCESS))
              log.info(s"[${hostString}] >> Sent D2CS_AUTHREPLY(Sequence #: ${seqno}, Result: ${D2CSAuthReply.RESULT_SUCCESS})")
          }
        case Packets.D2CS_ACCOUNTLOGINREQ =>
          data match {
            case D2CSAccountLoginRequest(packet) =>
              log.info(s"[${hostString}] >> Received D2CS_ACCOUNTLOGINREQ Sequence # ${seqno} for ${packet.sessionnum} and ${packet.accountName}")
              d2csActor ! AccountLoginRequest(connectionInfo, seqno, packet.sessionnum, packet.accountName)
          }
        case Packets.D2CS_CHARLOGINREQ =>
          data match {
            case D2CSCharLoginRequest(packet) =>
              log.info(s"[${hostString}] >> Received D2CS_CHARLOGINREQ Sequence # ${seqno} for ${packet.sessionnum} and ${packet.characterName}")
              d2csActor ! CharLoginRequest(connectionInfo, seqno, packet.sessionnum, packet.characterName, packet.characterPortrait)
          }
        case Packets.D2CS_GAMEINFOREQ =>
          data match {
            case D2CSGameInfoRequest(packet) =>
              log.info(s"[${hostString}] >> Received D2CS_GAMEINFOREQ Sequence # ${seqno} for ${packet.gameName}")
              d2csActor ! GameInfoRequest(connectionInfo, packet.gameName)
            case a =>
              log.info(s"[${hostString}] >> Unhandled {}", a.toString())
          }
        case _ =>
          log.info(s"[${hostString}] >> Unhandled 0x{}", f"$id%X")
      }
      stay()
    case a =>
      log.info(s"[${hostString}] >> Unhandled {}", a.toString)
      stay()
    case Event(Terminated(deadActor), _) =>
      log.error(s"Actor ${deadActor} was terminated unexpectedly!")
      stop()
  }

  def send(data: ByteString): Unit = {
    log.info(s"D2CSMessageHandler ${self} Writing out to actor: ${connectionInfo.actor}")
    connectionInfo.actor ! WriteOut(data)
  }

  override def preStart(): Unit = {
    log.info(s"D2CSMessageHandler started: ${self}")
  }

  onTermination {
    case x =>
      log.debug(">> {} D2CSMessageHandler onTermination: {}", connectionInfo.actor, x)
      connectionInfo.actor ! PoisonPill
      d2csActor ! RemoveRealm(connectionInfo)
  }
}