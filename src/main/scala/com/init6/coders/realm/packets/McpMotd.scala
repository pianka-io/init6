package com.init6.coders.realm.packets

import akka.util.ByteString
import com.init6.coders.binary.DeBuffer
import com.init6.coders.realm.RealmPacket

import scala.util.Try

/**
 * Created by pianka on 03/01/25.
 */
object McpMotd extends RealmPacket {

  override val PACKET_ID: Byte = Packets.MCP_MOTD

  def apply(motd: String): ByteString = {
    var packet = ByteString.newBuilder
      .putByte(0)
      .putBytes(motd)

    build(
      packet.result()
    )
  }
}