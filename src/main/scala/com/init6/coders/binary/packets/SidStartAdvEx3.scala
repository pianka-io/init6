package com.init6.coders.binary.packets

import akka.util.ByteString
import com.init6.coders.binary.{BinaryPacket, DeBuffer}

import java.io.File
import scala.util.Try

/**
 * Created by pianka on 03/02/25.
 */
object SidStartAdvEx3 extends BinaryPacket {

  override val PACKET_ID = Packets.SID_STARTADVEX3

  case class SidStartAdvEx3(
    gameState: Int,
    gameTime: Int,
    gameType: Short,
    subGameType: Short,
    version: Int,
    ladderType: Int,
    name: String,
    password: String,
    statstring: String
  )

  def apply(result: Int): ByteString = {
    build(
      ByteString.newBuilder
        .putInt(result)
        .result()
    )
  }

  val RESULT_SUCCESS = 0x00
  val RESULT_ALREADY_EXISTS = 0x01
  val RESULT_UNAVAILABLE = 0x02
  val RESULT_ERROR = 0x03

  def unapply(data: ByteString): Option[SidStartAdvEx3] = {
    Try {
      val debuffer = DeBuffer(data)
      SidStartAdvEx3(
        debuffer.dword(),
        debuffer.dword(),
        debuffer.word(),
        debuffer.word(),
        debuffer.dword(),
        debuffer.dword(),
        debuffer.string(),
        debuffer.string(),
        debuffer.string(),
      )
    }.toOption
  }
}
