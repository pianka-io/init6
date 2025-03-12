package com.init6.coders.binary.packets

import akka.util.ByteString
import com.init6.coders.binary.{BinaryPacket, DeBuffer}

import java.io.File
import scala.util.Try

/**
 * Created by pianka on 03/02/25.
 */
object SidNotifyJoin extends BinaryPacket {

  override val PACKET_ID = Packets.SID_NOTIFYJOIN

  case class SidNotifyJoin(
     productId: Int,
     productVersion: Int,
     name: String,
     password: String,
   )

  def unapply(data: ByteString): Option[SidNotifyJoin] = {
    Try {
      val debuffer = DeBuffer(data)
      SidNotifyJoin(
        debuffer.dword(),
        debuffer.dword(),
        debuffer.string(),
        debuffer.string(),
      )
    }.toOption
  }
}
