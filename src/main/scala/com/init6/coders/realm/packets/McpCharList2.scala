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

  def apply(characters: List[com.init6.realm.Character]): ByteString = {
    var packet = ByteString.newBuilder
      .putShort(8)
      .putInt(characters.length)
      .putShort(characters.length)

    characters.foreach(c => {
      packet = packet
        .putInt(Int.MaxValue)
        .putBytes(c.name)
        .putBytes(c.statstring.toBytes.toArray)
        .putByte(0)
    })

    build(
      packet.result()
    )
  }

  def unapply(data: ByteString): Option[McpCharList2] = {
    Try {
      val debuffer = DeBuffer(data)
      McpCharList2(debuffer.dword())
    }.toOption
  }
}