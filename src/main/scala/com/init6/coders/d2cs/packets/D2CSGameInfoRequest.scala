package com.init6.coders.d2cs.packets

import akka.util.ByteString
import com.init6.coders.binary.DeBuffer
import com.init6.coders.d2cs.D2CSPacket
import com.init6.coders.realm.RealmPacket

import scala.util.Try

/**
 * Created by pianka on 03/02/25.
 */
object D2CSGameInfoRequest extends D2CSPacket {

  override val PACKET_ID: Byte = Packets.D2CS_GAMEINFOREQ

  val RESULT_SUCCESS = 0x00
  val RESULT_FAILURE = 0x01

  case class D2CSGameInfoRequest(
    difficulty: Byte,
    gameName: String,
  )

  def apply(gameName: String): ByteString = {
    build(
      0,
      ByteString.newBuilder
//        .putByte(difficulty)
        .putBytes(gameName)
        .result()
    )
  }

  def unapply(data: ByteString): Option[D2CSGameInfoRequest] = {
    Try {
      val debuffer = DeBuffer(data)
      D2CSGameInfoRequest(
        debuffer.byte(),
        debuffer.string(),
      )
    }.toOption
  }
}