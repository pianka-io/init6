package com.init6.coders.realm.packets

import akka.util.ByteString
import com.init6.coders.binary.DeBuffer
import com.init6.coders.realm.RealmPacket

import scala.util.Try

/**
 * Created by pianka on 02/14/25.
 */
object McpCharCreate extends RealmPacket {

  override val PACKET_ID: Byte = Packets.MCP_CHARCREATE

  val RESULT_SUCCESS = 0x00
  val ALREADY_EXISTS = 0x14
  val INVALID = 0x15

  case class McpCharCreate(clazz: Int, flags: Int, name: String)

  def apply(result: Int): ByteString = {
    build(
      ByteString.newBuilder
        .putInt(result)
        .result()
    )
  }

  def unapply(data: ByteString): Option[McpCharCreate] = {
    Try {
      val debuffer = DeBuffer(data)
      McpCharCreate(debuffer.dword() + 1, debuffer.word(), debuffer.string())
    }.toOption
  }
}