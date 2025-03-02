package com.init6.connection.d2cs

import akka.util.ByteString
import com.init6.connection.PacketReceiver

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer

class D2CSReceiver extends PacketReceiver[D2CSPacket] {

  val HEADER_SIZE = 8

  @tailrec
  override final protected def parsePacketInternal(result: ArrayBuffer[D2CSPacket]): ArrayBuffer[D2CSPacket] = {
    if (buffer.length < HEADER_SIZE) {
      return result
    }

    val length = (buffer(1) << 8 & 0xFF00 | buffer(0) & 0xFF).toShort
    if (buffer.length < length) {
      return result
    }

    val packetId = buffer(2)
    val packet = buffer.slice(HEADER_SIZE, length)
    val seqno = (
      buffer(7) << 24 & 0xFF000000 |
      buffer(6) << 16 & 0x00FF0000 |
      buffer(5) << 8  & 0x0000FF00 |
      buffer(4)       & 0x000000FF
    )

    result += D2CSPacket(packetId, seqno, ByteString(packet.toArray))
    buffer = buffer.drop(length)
    if (buffer.nonEmpty) {
      parsePacketInternal(result)
    } else {
      result
    }
  }
}