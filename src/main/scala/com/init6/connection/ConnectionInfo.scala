package com.init6.connection

import java.net.InetSocketAddress

import akka.actor.ActorRef
import com.init6.users.{NotYetKnownProtocol, Protocol}

/**
  * Created by filip on 3/7/17.
  */
case class ConnectionInfo(
  ipAddress: InetSocketAddress,
  port: Int,
  actor: ActorRef,
  connectedTime: Long,
  firstPacketReceivedTime: Long = -1,
//  joiningTime: Long = -1,
  place: Int = -1,
  protocol: Protocol = NotYetKnownProtocol
)
