package com.init6.coders.realm.packets

import akka.util.ByteString
import com.init6.coders.binary.DeBuffer
import com.init6.coders.realm.RealmPacket

import scala.util.Try

/**
 * Created by pianka on 03/01/25.
 */
object McpCharDelete extends RealmPacket {

  override val PACKET_ID: Byte = Packets.MCP_CHARDELETE

  val RESULT_SUCCESS = 0x00
  val CHARACTER_NOT_FOUND = 0x49

  case class McpCharDelete(name: String)

  def apply(result: Int): ByteString = {
    build(
      ByteString.newBuilder
        .putInt(result)
        .result()
    )
  }

  def unapply(data: ByteString): Option[McpCharDelete] = {
    Try {
      val debuffer = DeBuffer(data)
      debuffer.word() // unknown
      McpCharDelete(debuffer.string())
    }.toOption
  }
}