package com.init6.connection.realm

import akka.actor.{ActorRef, FSM, Props}
import akka.util.ByteString
import com.init6.coders.realm.packets.McpCharList2
import com.init6.coders.realm.packets.McpStartup.RESULT_SUCCESS
import com.init6.coders.realm.packets.{McpStartup, Packets}
import com.init6.connection.{ConnectionInfo, Init6KeepAliveActor, WriteOut}

sealed trait RealmState
case object ExpectingStartup extends RealmState
case object ExpectingLogon extends RealmState

case class RealmPacket(packetId: Byte, packet: ByteString)

object RealmMessageHandler {
  def apply(connectionInfo: ConnectionInfo): Props = Props(classOf[RealmMessageHandler], connectionInfo)
}

class RealmMessageHandler(connectionInfo: ConnectionInfo) extends Init6KeepAliveActor with FSM[RealmState, ActorRef] {

  startWith(ExpectingStartup, ActorRef.noSender)
  context.watch(connectionInfo.actor)

  when (ExpectingStartup) {
    case Event(RealmPacket(id, data), _) =>
      id match {
        case Packets.MCP_STARTUP =>
          log.info(">> Received MCP_STARTUP")
          send(McpStartup(RESULT_SUCCESS))
          log.info("<< Sent MCP_STARTUP")
          goto(ExpectingLogon)
        case _ =>
          log.info(">> Received MCP packet {}", id)
          stay()
      }
    case x =>
      log.info(">> Received {}", x.toString)
      stay()
  }

  when (ExpectingLogon) {
    case Event(RealmPacket(id, data), _) =>
      id match {
        case Packets.MCP_CHARLIST2 =>
          log.info(">> Received MCP_CHARLIST2")
          send(McpCharList2())
          log.info("<< Sent MCP_CHARLIST2")
          stay()
        case _ =>
          log.info(">> Received MCP packet {}", id)
          stay()
      }
    case x =>
      log.info(">> Received {}", x.toString)
      stay()
  }

  def send(data: ByteString): Unit = {
    connectionInfo.actor ! WriteOut(data)
  }
}