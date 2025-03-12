package com.init6.coders.d2cs

import akka.util.ByteString

import java.nio.ByteOrder
import scala.language.implicitConversions

/**
 * Created by pianka on 02/14/25.
 */
trait D2CSPacket {

  implicit val byteOrder: ByteOrder = ByteOrder.LITTLE_ENDIAN

  val PACKET_HEADER_LENGTH: Short = 8

  val PACKET_ID: Byte

  implicit def stringToNTBytes(string: String): Array[Byte] = {
    Array.newBuilder[Byte]
      .++=(string.map(_.toByte))
      .+=(0)
      .result()
  }

  def build(seqno: Int, data: ByteString): ByteString = {
    ByteString.newBuilder
      .putShort(data.length + PACKET_HEADER_LENGTH)
      .putShort(PACKET_ID)
      .putInt(seqno)
      .append(data)
      .result()
  }
}
