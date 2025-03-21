package com.init6.coders.d2cs.packets

import akka.util.ByteString
import com.init6.coders.binary.DeBuffer
import com.init6.coders.d2cs.D2CSPacket

import scala.util.Try

/**
 * Created by pianka on 03/02/25.
 */
object D2CSAuthRequest extends D2CSPacket {

  override val PACKET_ID: Byte = Packets.D2CS_AUTHREQ

  case class D2CSAuthRequest(sessionnum: Int)

  def apply(result: Int): ByteString = {
    build(
      0,
      ByteString.newBuilder
        .putInt(result)
        .result()
    )
  }

  def unapply(data: ByteString): Option[D2CSAuthRequest] = {
    Try {
      val debuffer = DeBuffer(data)
      D2CSAuthRequest(debuffer.dword())
    }.toOption
  }
}