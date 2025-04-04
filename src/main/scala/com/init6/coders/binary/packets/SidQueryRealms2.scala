package com.init6.coders.binary.packets

import akka.util.ByteString
import com.init6.coders.binary.{BinaryPacket, DeBuffer}

import scala.util.Try

/**
 * Created by pianka on 02/14/25.
 */
object SidQueryRealms2 extends BinaryPacket {

  case class SidQueryRealms2(realms: List[String])

  override val PACKET_ID: Byte = Packets.SID_QUERYREALMS2

  def apply(realms: List[String]): ByteString = {

    val bs = ByteString.newBuilder
      .putInt(0) // unknown
      .putInt(realms.size) // realm count

    realms.foreach { name =>
      bs.putInt(1) // unknown
      bs.putBytes(name) // realm name
      bs.putBytes("Diablo II on init6 in 2025") // realm description
    }

    build(bs.result())
  }

  def unapply(data: ByteString): Option[SidQueryRealms2] = {
    Some(SidQueryRealms2(List.empty[String]))
  }
}
