package com.init6.coders.realm.packets

import akka.util.ByteString
import com.init6.coders.binary.DeBuffer
import com.init6.coders.realm.RealmPacket

import scala.util.Try

/**
 * Created by pianka on 02/14/25.
 */
object McpCharList2 extends RealmPacket {

  override val PACKET_ID: Byte = Packets.MCP_CHARLIST2

  case class McpCharList2(number: Int)

  def apply(): ByteString = {
    build(
      ByteString.newBuilder
        .putShort(8)
        .putInt(0)
        .putShort(0)
//        .putInt(Int.MaxValue)
//        .putBytes("piankachu")
//        .putBytes("VD2DSanctuary,piankachu")
        .result()
    )
  }

  def unapply(data: ByteString): Option[McpCharList2] = {
    Try {
      val debuffer = DeBuffer(data)
      McpCharList2(debuffer.dword())
    }.toOption
  }
}