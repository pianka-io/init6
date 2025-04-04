package com.init6.d2cs

import akka.actor.ActorRef
import com.init6.connection.ConnectionInfo

import scala.collection.mutable

case class D2CS(
  var realmName: String = "Unknown",
  d2csHandler: ActorRef,
  connectionInfo: ConnectionInfo,
  binaryHandler: mutable.HashMap[String, ActorRef],
  var sessionNum: Int = 1,
  userCache: mutable.HashMap[Int, String],
  characterCache: mutable.HashMap[Int, String],
  gameCache: mutable.HashMap[String, String]
)
