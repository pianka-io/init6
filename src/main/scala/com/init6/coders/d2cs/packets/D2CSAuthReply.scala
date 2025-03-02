package com.init6.coders.d2cs.packets

import akka.util.ByteString
import com.init6.coders.binary.DeBuffer
import com.init6.coders.realm.RealmPacket

import scala.util.Try

/**
 * Created by pianka on 03/02/25.
 */
object D2CSAuthReply extends RealmPacket {

  override val PACKET_ID: Byte = Packets.D2CS_AUTHREPLY

  val RESULT_SUCCESS = 0x00
  val RESULT_BAD_VERSION = 0x01

  case class D2CSAuthReply(version: Int, realmName: String)

  def apply(result: Int): ByteString = {
    build(
      ByteString.newBuilder
        .putInt(result)
        .result()
    )
  }

  def unapply(data: ByteString): Option[D2CSAuthReply] = {
    Try {
      val debuffer = DeBuffer(data)
      D2CSAuthReply(debuffer.dword(), debuffer.string())
    }.toOption
  }
}