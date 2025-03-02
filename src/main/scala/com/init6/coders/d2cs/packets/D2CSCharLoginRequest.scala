package com.init6.coders.d2cs.packets

import akka.util.ByteString
import com.init6.coders.binary.DeBuffer
import com.init6.coders.realm.RealmPacket

import scala.util.Try

/**
 * Created by pianka on 03/02/25.
 */
object D2CSCharLoginRequest extends RealmPacket {

  override val PACKET_ID: Byte = Packets.D2CS_CHARLOGINREQ

  val RESULT_SUCCESS = 0x00
  val RESULT_FAILURE = 0x01

  case class D2CSCharLoginRequest(
    sessionnum: Int,
    characterName: String,
    characterPortrait: ByteString, // statstring
  )

  def apply(result: Int): ByteString = {
    build(
      ByteString.newBuilder
        .putInt(result)
        .result()
    )
  }

  def unapply(data: ByteString): Option[D2CSCharLoginRequest] = {
    Try {
      val debuffer = DeBuffer(data)
      D2CSCharLoginRequest(
        debuffer.dword(),
        debuffer.string(),
        ByteString(debuffer.byteArray(33))
      )
    }.toOption
  }
}