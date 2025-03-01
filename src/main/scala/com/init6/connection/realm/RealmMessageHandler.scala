package com.init6.connection.realm

import akka.actor.{ActorRef, FSM, Props}
import akka.util.ByteString
import com.init6.coders.realm.packets.{McpCharCreate, McpCharList2, McpCharLogon, McpStartup, Packets}
import com.init6.coders.realm.packets.McpStartup.RESULT_SUCCESS
import com.init6.connection.{ConnectionInfo, Init6KeepAliveActor, WriteOut}
import com.init6.db.{RealmCreateCharacter, RealmCreateCharacterAck, RealmReadCharacter, RealmReadCharacterResponse, RealmReadCharacters, RealmReadCharactersResponse, RealmReadCookie, RealmReadCookieResponse}
import com.init6.users.SetCharacter


sealed trait RealmState
case object ExpectingStartup extends RealmState
case object ExpectingLogon extends RealmState

case object ExpectingRealmCookieReadFromDAO extends RealmState
case object ExpectingRealmCharactersCreateFromDAO extends RealmState
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
          log.info(">> Received MCP packet {}", id)
          stay()
      }
    case x =>
      log.info(">> Received {}", x.toString)
      stay()
  }

  when (ExpectingRealmCookieReadFromDAO) {
    case Event(RealmReadCookieResponse(userId, username), _) =>
      send(McpStartup(RESULT_SUCCESS))
      this.userId = userId
      this.username = username
      log.info("<< Sent MCP_STARTUP")
      goto(ExpectingLogon)
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
          log.info(">> Received MCP_CHARLIST2")
          daoActor ! RealmReadCharacters(userId)
          goto(ExpectingRealmCharactersReadFromDAO)
        case Packets.MCP_CHARCREATE =>
          log.info(">> Received MCP_CHARCREATE")
          data match {
            case McpCharCreate(packet) =>
              daoActor ! RealmCreateCharacter(userId, packet.name, packet.clazz, packet.flags)
              goto(ExpectingRealmCharactersCreateFromDAO)
            case _ => stop()
          }
        case _ =>
          log.info(">> Received MCP packet {}", id)
          stay()
      }
    case x =>
      log.info(">> Received {}", x.toString)
      stay()
  }

  when (ExpectingRealmCharactersReadFromDAO) {
    case Event(RealmReadCharactersResponse(characters), _) =>
      send(McpCharList2(characters))
      log.info("<< Sent MCP_CHARLIST2")
      // TODO(pianka): update user's statstring
      goto(ExpectingLogon)
  }

  when (ExpectingRealmCharacterReadFromDAO) {
    case Event(RealmReadCharacterResponse(character), _) =>
      send(McpCharLogon(McpCharLogon.RESULT_SUCCESS))
      log.info("<< Sent MCP_CHARLOGON")
      sendCharacter(character)
      log.info("<< Sent MCP_CHARCREATE")
      goto(ExpectingGame)
  }

  when (ExpectingRealmCharactersCreateFromDAO) {
    case Event(RealmCreateCharacterAck(result, character), _) =>
      // TODO(pianka): honor the result
      send(McpCharCreate(RESULT_SUCCESS))
      log.info("<< Sent MCP_CHARCREATE")
      sendCharacter(character)
      log.info("<< Sent MCP_CHARCREATE")
      goto(ExpectingLogon)
  }

  def send(data: ByteString): Unit = {
    connectionInfo.actor ! WriteOut(data)
  }

  private def sendCharacter(character: com.init6.realm.Character): Unit = {
    usersActor ! SetCharacter(username, character)
  }
}