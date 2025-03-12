package com.init6.coders.realm.packets

import akka.util.ByteString
import com.init6.coders.binary.DeBuffer
import com.init6.coders.realm.RealmPacket
import com.init6.realm.Game

import scala.util.Try

/**
 * Created by pianka on 03/01/25.
 */
object McpGameList extends RealmPacket {

  override val PACKET_ID: Byte = Packets.MCP_GAMELIST

  case class McpGameList(requestId: Short, unknown: Int, search: String)

  def apply(requestId: Short, index: Int, game: Option[Game] = None): ByteString = {
    val name = game.map(_.name).getOrElse("")
    val description = game.map(_.description).getOrElse("")
    val packet = ByteString.newBuilder
      .putShort(requestId)
      .putInt(index)
      .putByte(game.map(_.players.size).getOrElse(0).toByte)
      .putInt(0xFFFFFFFF)
      .putBytes(name)
      .putBytes(description)

    build(
      packet.result()
    )
  }

  def unapply(data: ByteString): Option[McpGameList] = {
    Try {
      val debuffer = DeBuffer(data)
      McpGameList(debuffer.word(), debuffer.dword(), debuffer.string())
    }.toOption
  }
}