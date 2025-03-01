package com.init6.coders.realm.packets

import akka.util.ByteString
import com.init6.coders.binary.DeBuffer
import com.init6.coders.realm.RealmPacket

import scala.util.Try

/**
 * Created by pianka on 03/01/25.
 */
object McpGameCreate extends RealmPacket {

  override val PACKET_ID: Byte = Packets.MCP_GAME_CREATE

  val RESULT_SUCCESS = 0x00
  val INVALID_NAME = 0x1E
  val ALREADY_EXISTS = 0x1F
  val SERVERS_DOWN = 0x20
  val HARDCORE_DEAD = 0x6E

  case class McpCreateGame(
    requestId: Short,
    difficulty: Int,
    unknown: Byte,
    levelDifference: Byte,
    maximumPlayers: Byte,
    name: String,
    password: String,
    description: String,
  )

  def apply(requestId: Short, gameToken: Short, result: Int): ByteString = {
    build(
      ByteString.newBuilder
        .putShort(requestId)
        .putShort(gameToken)
        .putShort(0)
        .putInt(result)
        .result()
    )
  }

  def unapply(data: ByteString): Option[McpCreateGame] = {
    Try {
      val debuffer = DeBuffer(data)
      McpCreateGame(
        debuffer.word(),
        debuffer.dword(),
        debuffer.byte(),
        debuffer.byte(),
        debuffer.byte(),
        debuffer.string(),
        debuffer.string(),
        debuffer.string(),
      )
    }.toOption
  }
}