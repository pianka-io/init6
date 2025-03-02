package com.init6.coders.binary.packets

import akka.util.ByteString
import com.init6.Config
import com.init6.coders.binary.{BinaryPacket, DeBuffer}

import java.net.InetAddress
import java.nio.ByteBuffer
import scala.util.Try

/**
 * Created by pianka on 02/14/25.
 */
object SidLogonRealmEx extends BinaryPacket {

  case class SidLogonRealmEx(title: String)

  override val PACKET_ID: Byte = Packets.SID_LOGONREALMEX

  /**
   * [Note this format is slightly different from BNETDocs reference as of 2023-02-18]
   * (UINT32)     MCP Cookie (Client Token)
   * (UINT32)     MCP Status
   * (UINT32)[2]  MCP Chunk 1
   * (UINT32)     IP
   * (UINT32)     Port
   * (UINT32)[12] MCP Chunk 2
   * (STRING)     Battle.net unique name (* as of D2 1.14d, this is empty)
   */
  def apply(cookie: Int, username: String): ByteString = {
    build(
      ByteString.newBuilder
        .putInt(cookie) // cookie
        .putInt(0x00000000) // status
        .putInt(0x00000000) // mcp chunk 1.1
        .putInt(0x00000001) // mcp chunk 1.2
        .putInt(aton(Config().Realm.ipAddress)) // ip
        .putInt(htons(6113)) // port
        .putInt(0x67C48058) // mcp chunk 2
        .putInt(0x00000000) // magic
        .putInt(0x00000000)
        .putInt(0x44324456)
        .putInt(0x00000000)
        .putInt(0x00000000)
        .putInt(0x00000000)
        .putInt(0x483EE33F)
        .putInt(0x4AE508D5)
        .putInt(0x21886A56)
        .putInt(0x5F15CE41)
        .putInt(0x87A1586D)
        .putBytes(username)
        .result()
    )
  }

  def unapply(data: ByteString): Option[SidLogonRealmEx] = {
    Some(SidLogonRealmEx("Sanctuary"))
  }

  def aton(ip: String): Int = {
    val bytes = InetAddress.getByName(ip).getAddress
    ByteBuffer.wrap(bytes.reverse).getInt
  }

  def htons(value: Int): Int = ((value & 0xFF) << 8) | ((value >> 8) & 0xFF)
}
