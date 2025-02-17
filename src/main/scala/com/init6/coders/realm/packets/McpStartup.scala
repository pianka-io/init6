package com.init6.coders.realm.packets

import akka.util.ByteString
import com.init6.coders.binary.DeBuffer
import com.init6.coders.realm.RealmPacket

import scala.util.Try

/**
 * Created by pianka on 02/14/25.
 */
object McpStartup extends RealmPacket {

  override val PACKET_ID: Byte = Packets.MCP_STARTUP

  val RESULT_SUCCESS = 0x00
  val RESULT_UNAVAILABLE = 0x02
  val RESULT_BANNED = 0x7E
  val RESULT_TEMP_BANNED = 0x7F

  case class McpStartup(cookie: Int)

  def apply(result: Int): ByteString = {
    build(
      ByteString.newBuilder
        .putInt(result)
        .result()
    )
  }

  def unapply(data: ByteString): Option[McpStartup] = {
    Try {
      val debuffer = DeBuffer(data)
      McpStartup(debuffer.dword())
    }.toOption
  }
}