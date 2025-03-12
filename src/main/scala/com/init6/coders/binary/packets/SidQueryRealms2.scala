package com.init6.coders.binary.packets

import akka.util.ByteString
import com.init6.coders.binary.{BinaryPacket, DeBuffer}

import scala.util.Try

/**
 * Created by pianka on 02/14/25.
 */
object SidQueryRealms2 extends BinaryPacket {

  case class SidQueryRealms2()

  override val PACKET_ID: Byte = Packets.SID_QUERYREALMS2

  def apply(): ByteString = {
    build(
      ByteString.newBuilder
        .putInt(0) // unknown
        .putInt(1) // count
        .putInt(1) // unknown
        .putBytes("Sanctuary") // title
        .putBytes("Diablo II on init6 in 2025") // description
        .result()
    )
  }

  def unapply(data: ByteString): Option[SidQueryRealms2] = {
    Some(SidQueryRealms2())
  }
}
