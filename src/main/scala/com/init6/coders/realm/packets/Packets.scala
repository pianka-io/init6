package com.init6.coders.realm.packets

/**
 * Created by pianka on 02/14/25.
 */
object Packets {

  val MCP_NULL: Byte = 0x00
  val MCP_STARTUP: Byte = 0x01
  val MCP_CHARCREATE: Byte = 0x02
  val MCP_GAMECREATE: Byte = 0x03
  val MCP_GAMEJOIN: Byte = 0x04
  val MCP_GAMELIST: Byte = 0x05
  val MCP_GAMEINFO: Byte = 0x06
  val MCP_CHARLOGON: Byte = 0x07
  val MCP_CHARDELETE: Byte = 0x0A
  val MCP_REQUESTLADDERDATA: Byte = 0x11
  val MCP_MOTD: Byte = 0x12
  val MCP_CANCELGAMECREATE: Byte = 0x13
  val MCP_CHARRANK: Byte = 0x16
  val MCP_CHARLIST: Byte = 0x17
  val MCP_CHARUPGRADE: Byte = 0x18
  val MCP_CHARLIST2: Byte = 0x19
}