package com.init6.coders.realm.packets

import akka.util.ByteString
import com.init6.coders.binary.DeBuffer
import com.init6.coders.realm.RealmPacket

import scala.util.Try

/**
 * Created by pianka on 02/28/25.
 */
object McpCharLogon extends RealmPacket {

  override val PACKET_ID: Byte = Packets.MCP_CHARLOGON

  val RESULT_SUCCESS = 0x00
  val CHARACTER_NOT_FOUND = 0x46
  val LOGON_FAILED = 0x7A
  val CHARACTER_EXPIRED = 0x7B

  case class McpCharLogon(name: String)

  def apply(result: Int): ByteString = {
    build(
      ByteString.newBuilder
        .putInt(result)
        .result()
    )
  }

  def unapply(data: ByteString): Option[McpCharLogon] = {
    Try {
      val debuffer = DeBuffer(data)
      McpCharLogon(debuffer.string())
    }.toOption
  }
}