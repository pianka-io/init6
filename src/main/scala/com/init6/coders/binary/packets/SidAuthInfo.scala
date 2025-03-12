package com.init6.coders.binary.packets

import akka.util.ByteString
import com.init6.coders.binary.{BinaryPacket, DeBuffer}

import java.io.File
import scala.util.Try

/**
 * Created by filip on 10/25/15.
 */
object SidAuthInfo extends BinaryPacket {

  override val PACKET_ID = Packets.SID_AUTH_INFO

  val LAST_MODIFIED = new File("bnftp/ver-IX86-0.mpq").lastModified()

  def apply(serverToken: Int, udpToken: Int = 0xDEADBEEF): ByteString = {
    build(
      ByteString.newBuilder
        .putInt(0)
        .putInt(serverToken)
        .putInt(udpToken)
        .putLong(LAST_MODIFIED)
        .putBytes("ver-IX86-0.mpq")
        .putBytes("A=125933019 B=665814511 C=736475113 4 A=A+S B=B^C C=C^A A=A^B")
        .result()
    )
  }

  case class SidAuthInfo(productId: String, versionByte: Byte)

  def unapply(data: ByteString): Option[SidAuthInfo] = {
    Try {
      val debuffer = DeBuffer(data)
      debuffer.skip(8)
      val productId = debuffer.byteArray(4)
      val verbyte = debuffer.byte(8)
      SidAuthInfo(new String(productId), verbyte)
    }.toOption
  }
}
