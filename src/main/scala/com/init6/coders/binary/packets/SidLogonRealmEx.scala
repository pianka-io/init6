package com.init6.coders.binary.packets

import akka.util.ByteString
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
  def apply(): ByteString = {
    build(
      ByteString.newBuilder
        .putInt(0xDEADBEEF) // cookie
        .putInt(0x00000000) // status
        .putInt(0x33316163) // mcp chunk 1.1
        .putInt(0x65303830) // mcp chunk 1.2
        .putInt(aton("127.0.0.1")) // ip
        .putInt(htons(4000)) // port
        .putInt(0x66663162) // mcp chunk 2
        .putInt(0x34613566) // magic
        .putInt(0x64326639)
        .putInt(0x63336330)
        .putInt(0x38326135)
        .putInt(0x39663937)
        .putInt(0x62653134)
        .putInt(0x36313861)
        .putInt(0x36353032)
        .putInt(0x31353066)
        .putInt(0x00000000)
        .putInt(0x00000000)
        .putByte(0x00) // empty string
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
