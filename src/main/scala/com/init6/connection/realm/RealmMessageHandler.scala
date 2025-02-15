package com.init6.connection.realm

import akka.actor.{ActorRef, FSM, Props}
import akka.util.ByteString
import com.init6.connection.{ConnectionInfo, Init6KeepAliveActor}

sealed trait RealmState
case object ExpectingStartup extends RealmState

case class RealmPacket(packetId: Byte, packet: ByteString)

object RealmMessageHandler {
  def apply(connectionInfo: ConnectionInfo) = Props(classOf[RealmMessageHandler], connectionInfo)
}

class RealmMessageHandler(connectionInfo: ConnectionInfo) extends Init6KeepAliveActor with FSM[RealmState, ActorRef] {

  startWith(ExpectingStartup, ActorRef.noSender)
  context.watch(connectionInfo.actor)

  when (ExpectingStartup) {
    case Event(RealmPacket(id, data), _) =>
      log.info(">> Received MCP packet {}", id)
      stay()
    case x =>
      log.info(">> Received {}", x.toString)
      stay()
  }
}