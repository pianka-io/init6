package com.init6.connection

import akka.util.ByteString
import com.init6.connection.realm.RealmPacket

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer

class RealmReceiver extends PacketReceiver[RealmPacket] {

  val HEADER_SIZE = 3

  @tailrec
  override final protected def parsePacketInternal(result: ArrayBuffer[RealmPacket]): ArrayBuffer[RealmPacket] = {
    if (buffer.length < HEADER_SIZE) {
      return result
    }

    val length = (buffer(1) << 8 & 0xFF00 | buffer(0) & 0xFF).toShort
    if (buffer.length < length) {
      return result
    }

    val packetId = buffer(2)
    val packet = buffer.slice(HEADER_SIZE, length)

    result += RealmPacket(packetId, ByteString(packet.toArray))
    buffer = buffer.drop(length)
    if (buffer.nonEmpty) {
      parsePacketInternal(result)
    } else {
      result
    }
  }
}