package com.init6.connection.realm

import akka.actor.{ActorRef, FSM, Props}
import akka.util.ByteString
import com.init6.coders.realm.packets.{McpCharCreate, McpCharDelete, McpCharList2, McpCharLogon, McpGameCreate, McpGameList, McpMotd, McpStartup, Packets}
import com.init6.coders.realm.packets.McpStartup.RESULT_SUCCESS
import com.init6.connection.{ConnectionInfo, Init6KeepAliveActor, WriteOut}
import com.init6.db.{RealmCreateCharacter, RealmCreateCharacterAck, RealmDeleteCharacter, RealmDeleteCharacterAck, RealmReadCharacter, RealmReadCharacterResponse, RealmReadCharacters, RealmReadCharactersResponse, RealmReadCookie, RealmReadCookieResponse}
import com.init6.users.SetCharacter


sealed trait RealmState
case object ExpectingStartup extends RealmState
case object ExpectingLogon extends RealmState
case object ExpectingGame extends RealmState

case object ExpectingRealmCookieReadFromDAO extends RealmState
case object ExpectingRealmCharacterCreateFromDAO extends RealmState
case object ExpectingRealmCharacterDeleteFromDAO extends RealmState
case object ExpectingRealmCharactersReadFromDAO extends RealmState
case object ExpectingRealmCharacterReadFromDAO extends RealmState

case class RealmPacket(packetId: Byte, packet: ByteString)

object RealmMessageHandler {
  def apply(connectionInfo: ConnectionInfo): Props = Props(classOf[RealmMessageHandler], connectionInfo)
}

class RealmMessageHandler(connectionInfo: ConnectionInfo) extends Init6KeepAliveActor with FSM[RealmState, ActorRef] {

  var userId: Long = _
  var username: String = _

  startWith(ExpectingStartup, ActorRef.noSender)
  context.watch(connectionInfo.actor)

  when (ExpectingStartup) {
    case Event(RealmPacket(id, data), _) =>
      id match {
        case Packets.MCP_STARTUP =>
          log.info(">> Received MCP_STARTUP")
          data match {
            case McpStartup(packet) =>
              log.info(">> Retrieving realm cookie")
              daoActor ! RealmReadCookie(packet.cookie)
              goto(ExpectingRealmCookieReadFromDAO)
          }
        case _ =>
          log.info("[ExpectingLogon] Unhandled 0x{}", f"$id%X")
          stay()
      }
    case a =>
      log.info("[ExpectingRealmCookieReadFromDAO] Unhandled {}", a.toString)
      stay()
  }

  when (ExpectingRealmCookieReadFromDAO) {
    case Event(RealmReadCookieResponse(userId, username), _) =>
      send(McpStartup(RESULT_SUCCESS))
      this.userId = userId
      this.username = username
      log.info("<< Sent MCP_STARTUP")
      goto(ExpectingLogon)
    case a =>
      log.info("[ExpectingRealmCookieReadFromDAO] Unhandled {}", a.toString)
      stay()
  }

  when (ExpectingLogon) {
    case Event(RealmPacket(id, data), _) =>
      id match {
        case Packets.MCP_CHARLOGON =>
          log.info(">> Received MCP_CHARLOGON")
          data match {
            case McpCharLogon(packet) =>
              daoActor ! RealmReadCharacter(userId, packet.name)
              goto(ExpectingRealmCharacterReadFromDAO)
            case _ => stop()
          }
        case Packets.MCP_CHARLIST2 =>
          log.info(">> Received MCP_CHARLIST2 B")
          daoActor ! RealmReadCharacters(userId)
          goto(ExpectingRealmCharactersReadFromDAO)
        case Packets.MCP_CHARCREATE =>
          log.info(">> Received MCP_CHARCREATE")
          data match {
            case McpCharCreate(packet) =>
              daoActor ! RealmCreateCharacter(userId, packet.name, packet.clazz, packet.flags)
              goto(ExpectingRealmCharacterCreateFromDAO)
            case _ => stop()
          }
        case Packets.MCP_CHARDELETE =>
          log.info(">> Received MCP_CHARDELETE")
          data match {
            case McpCharDelete(packet) =>
              daoActor ! RealmDeleteCharacter(userId, packet.name)
              goto(ExpectingRealmCharacterDeleteFromDAO)
            case _ => stop()
          }
        case Packets.MCP_MOTD =>
          send(McpMotd("Welcome to Warnet 2025: Sanctuary"))
          stay()
        case _ =>
          log.info("[ExpectingLogon] Unhandled 0x{}", f"$id%X")
          stay()
      }
    case a =>
      log.info("[ExpectingRealmCookieReadFromDAO] Unhandled {}", a.toString)
      stay()
  }

  when (ExpectingRealmCharactersReadFromDAO) {
    case Event(RealmReadCharactersResponse(characters), _) =>
      send(McpCharList2(characters))
      log.info("<< Sent MCP_CHARLIST2")
      // TODO(pianka): update user's statstring
      goto(ExpectingLogon)
    case a =>
      log.info("[ExpectingRealmCharactersReadFromDAO] Unhandled {}", a.toString)
      stay()
  }

  when (ExpectingRealmCharacterReadFromDAO) {
    case Event(RealmReadCharacterResponse(character), _) =>
      sendCharacter(character)
      log.info("[ExpectingRealmCharacterReadFromDAO] Statstring {}", character.statstring.toBytes)
      send(McpCharLogon(McpCharLogon.RESULT_SUCCESS))
      log.info("<< Sent MCP_CHARLOGON")
      goto(ExpectingGame)
    case a =>
      log.info("[ExpectingRealmCharacterReadFromDAO] Unhandled {}", a.toString)
      stay()
  }

  when (ExpectingRealmCharacterCreateFromDAO) {
    case Event(RealmCreateCharacterAck(result, character), _) =>
      // TODO(pianka): honor the result
      send(McpCharCreate(RESULT_SUCCESS))
      sendCharacter(character)
      log.info("<< Sent MCP_CHARCREATE")
      goto(ExpectingGame)
    case a =>
      log.info("[ExpectingRealmCharactersCreateFromDAO] Unhandled {}", a.toString)
      stay()
  }

  when (ExpectingRealmCharacterDeleteFromDAO) {
    case Event(RealmDeleteCharacterAck(result), _) =>
      // TODO(pianka): honor the result
      send(McpCharDelete(RESULT_SUCCESS))
      log.info("<< Sent MCP_CHARDELETE")
      goto(ExpectingLogon)
    case a =>
      log.info("[ExpectingRealmCharacterDeleteFromDAO] Unhandled {}", a.toString)
      stay()
  }

  when (ExpectingGame) {
    case Event(RealmPacket(id, data), _) =>
      id match {
        case Packets.MCP_GAMELIST =>
          data match {
            case McpGameList(packet) =>
              send(McpGameList(packet.requestId, 0, None))
          }
          stay()
        case Packets.MCP_GAMECREATE =>
          data match {
            case McpGameCreate(packet) =>
              send(McpGameCreate(packet.requestId, 0, McpGameCreate.SERVERS_DOWN))
          }
          stay()
        case Packets.MCP_CHARLIST2 =>
          log.info(">> Received MCP_CHARLIST2 A")
          daoActor ! RealmReadCharacters(userId)
          goto(ExpectingRealmCharactersReadFromDAO)
        case Packets.MCP_MOTD =>
          send(McpMotd("Welcome to Warnet 2025: Sanctuary"))
          stay()
        case _ =>
          log.info("[ExpectingGame] Unhandled 0x{}", f"$id%X")
          stay()
      }
    case a =>
      log.info("[ExpectingGame] Unhandled {}", a.toString)
      stay()
  }

  def send(data: ByteString): Unit = {
    connectionInfo.actor ! WriteOut(data)
  }

  private def sendCharacter(character: com.init6.realm.Character): Unit = {
    usersActor ! SetCharacter(username, character.name, character.statstring.toBytes)
  }
}