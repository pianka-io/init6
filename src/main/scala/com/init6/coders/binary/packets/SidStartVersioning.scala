package com.init6.coders.binary.packets

import akka.util.ByteString
import com.init6.coders.binary.{BinaryPacket, DeBuffer}

import java.io.File
import scala.util.Try

/**
 * Created by filip on 10/28/15.
 */
object SidStartVersioning extends BinaryPacket {

  override val PACKET_ID: Byte = Packets.SID_STARTVERSIONING

  val LAST_MODIFIED = new File("bnftp/ver-IX86-0.mpq").lastModified()

  def apply(): ByteString = {
    build(
      ByteString.newBuilder
        .putLong(LAST_MODIFIED)
        .putBytes("ver-IX86-0.mpq")
        .putBytes("A=125933019 B=665814511 C=736475113 4 A=A+S B=B^C C=C^A A=A^B")
        .result()
    )
  }

  def unapply(data: ByteString): Option[SidStartVersioning] = {
    Try {
      val debuffer = DeBuffer(data)
      debuffer.skip(4)
      val productId = debuffer.byteArrayAsString(4)
      val versionByte = debuffer.byte()
      SidStartVersioning(productId, versionByte)
    }.toOption
  }
}

case class SidStartVersioning(productId: String, versionByte: Byte)
