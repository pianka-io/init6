package com.init6.connection.d2cs

import akka.util.ByteString
import akka.actor.{Actor, ActorRef, FSM, Props, Terminated}
import com.init6.Constants.D2CS_REALMS_PATH
import com.init6.coders.binary.packets.SidStartAdvEx3
import com.init6.connection.ConnectionInfo
import com.init6.{Init6Actor, Init6Component, Init6LoggingActor}
import com.init6.d2cs.D2CS
import com.init6.coders.d2cs.packets.{D2CSAccountLoginRequest, D2CSAuthReply, D2CSAuthRequest, D2CSCharLoginRequest, D2CSGameInfoRequest, Packets}
import com.init6.users.SetCharacter

import scala.collection.mutable

//Flow order between BinaryMessageHandler, D2CSMessageHandler, and D2CSActor
case class RegisterRealm(d2csHandler: ActorRef, connectionInfo: ConnectionInfo)
case class D2CSRegistered(connectionInfo: ConnectionInfo, sessionNum: Int)
case class SetRealmName(connectionInfo: ConnectionInfo, name: String)
case class GetRealms(connectionInfo: ConnectionInfo)
case class RealmNamesList(realmList: List[String])
case class GetRealmLoginInfo(connectionInfo: ConnectionInfo, cookie: Int, realmName: String, oldUsername: String)
case class RealmLoginInfoResponse(realmName: String, cookie: Int, ip: String, port: Int, username: String)
case class AccountLoginRequest(connectionInfo: ConnectionInfo, seqNum: Int, sessionNum: Int, accountName: String)
case class ReturnAccountLoginRequest(seqNo: Int, accountName: String, result: Int)
case class CharLoginRequest(connectionInfo: ConnectionInfo, seqNum: Int, sessionNum: Int, characterName: String, characterPortrait: ByteString)
case class ReturnCharLoginRequest(seqNum: Int, sessionNum: Int, accountName: String, characterName: String, RESULT_FAILURE: Int)
case class ReceivedGameRequest(binaryActor: ActorRef, accountName:String, realmName: String, name: String)
case class ReceivedGameResponse(successful: Int)
case class GameInfoRequest(connectionInfo: ConnectionInfo, gameName: String)

case class RemoveRealm(connectionInfo: ConnectionInfo)

object D2CSActor extends Init6Component {
  def apply() = system.actorOf(Props[D2CSActor], D2CS_REALMS_PATH)
}

class D2CSActor extends Init6Actor with Init6LoggingActor {
  val actorPath = D2CS_REALMS_PATH
  var sessionNum = 1

  val realmConnections = mutable.Map.empty[ActorRef, D2CS]

  def loggedReceive: Receive = {
    case RegisterRealm(d2csHandler, connectionInfo) =>
      val actorRef = connectionInfo.actor
      val newD2CS = D2CS(
        d2csHandler = d2csHandler,
        sessionNum = sessionNum,
        connectionInfo = connectionInfo,
        binaryHandler = mutable.HashMap.empty[String, ActorRef],
        userCache = mutable.HashMap.empty[Int, String],
        characterCache = mutable.HashMap.empty[Int, String],
        gameCache = mutable.HashMap.empty[String, String]
      )
      if (!realmConnections.contains(actorRef)) {
        // Register the ConnectionInfo (which contains the actor and other relevant data)
        realmConnections.put(actorRef, newD2CS)
        //log.info(s"Registered realm connection for actor: $actorRef")
        sender() ! D2CSRegistered(connectionInfo, newD2CS.sessionNum)
        sessionNum += 1
      }
    case SetRealmName(connectionInfo, realmName) =>
      realmConnections.get(connectionInfo.actor) match {
        case Some(info) =>
          log.info(s"D2CS ConnectInfo $connectionInfo set Realm name: $realmName.")
          info.realmName = realmName
        case None =>
          log.error(s"D2CS SetRealmName failed! ConnectionInfo: $connectionInfo not found in realm list.")
      }
    case GetRealms(connectionInfo) =>
      val allRealmNames = realmConnections.values.map(_.realmName).toList
      sender() ! RealmNamesList(allRealmNames)
    case GetRealmLoginInfo(connectionInfo, cookie, targetRealmName, username) => //Not used. Using config for now.
      val maybeInfo = realmConnections.values.find(_.realmName.equalsIgnoreCase(targetRealmName))

      maybeInfo match {
        case Some(info) =>
          val ip   = info.connectionInfo.ipAddress.getAddress.getHostAddress
          val port = info.connectionInfo.port // Can't use this because it's connection is on 6112...
          sender() ! RealmLoginInfoResponse(info.realmName, cookie, ip, port, username)

        case None =>
          log.warning(s"Requested realm '$targetRealmName' not found.")
          sender() ! RealmLoginInfoResponse(targetRealmName, cookie, "127.0.0.1", 6113, username)
      }
    case AccountLoginRequest(connectionInfo, seqNum, sessionNum, accountName) =>
      realmConnections.get(connectionInfo.actor) match {
        case Some(info) =>
          if(info.userCache.values.exists(_.toLowerCase == accountName.toLowerCase)){
            val existingKey = info.userCache.collectFirst {
              case (key, value) if value.toLowerCase == accountName.toLowerCase => key
            }
            log.debug(s"D2CS AccountLoginRequest: Account Name found! Session number: $existingKey.")
            existingKey.foreach(info.userCache -= _)
          } else if (info.userCache.contains(sessionNum)) {
            log.error(s"Session Number: $sessionNum already in D2CS User Cache for ${info.realmName}.")
            info.userCache.remove(sessionNum)
          }
          info.userCache += sessionNum -> accountName
          log.debug(s"D2CS AccountLoginRequest: Account Name added! $accountName.")
          sender() ! ReturnAccountLoginRequest(seqNum, accountName, D2CSAccountLoginRequest.RESULT_SUCCESS)
        case None =>
          sender() ! ReturnAccountLoginRequest(seqNum, accountName, D2CSAccountLoginRequest.RESULT_FAILURE)
      }
    case CharLoginRequest(connectionInfo, seqNum, sessionNum, characterName, characterPortrait) =>
      realmConnections.get(connectionInfo.actor) match {
        case Some(info) =>
          info.userCache.get(sessionNum).foreach(u =>
            usersActor ! SetCharacter(u, characterName, characterPortrait)
          )
          /*
          Removed characterCache.
          Not needed for now and checking for character already connected is hard to do since we don't know if one disconnects.
          Also, if you don't join a game then the client re-logs. The server doesn't know that and if we're tracking unique character logins that creates problems.
           */
          val charsUsername = info.userCache.getOrElse(sessionNum, "Unknown")
          log.debug(s"D2CS CharLoginRequest: Found username: $charsUsername for character $characterName.")

          // Proceed with login without checking or updating characterCache
          sender() ! ReturnCharLoginRequest(seqNum, sessionNum, charsUsername, characterName, D2CSCharLoginRequest.RESULT_SUCCESS)

        case None =>
          log.debug(s"D2CS CharLoginRequest: Realm not found! Character login for $characterName failed!")
          sender() ! ReturnCharLoginRequest(seqNum, sessionNum, "Unknown", characterName, D2CSCharLoginRequest.RESULT_FAILURE)
      }

    case ReceivedGameRequest(binaryHandler, accountName, realmName, gameName) =>
      log.debug(s"D2CS ReceivedGameRequest: $accountName on $realmName trying to create game: $gameName")

      val matchingRealm = realmConnections.values.find { info =>
        info.realmName.toLowerCase == realmName.toLowerCase
      }

      matchingRealm match {
        case Some(info) =>
          info.binaryHandler.put(accountName, binaryHandler)
          if (!info.gameCache.contains(gameName.toLowerCase)) {
            info.gameCache.put(gameName, accountName)
            info.d2csHandler ! GameInfoRequest(info.connectionInfo, gameName) //Might not need to do this? Client tells realm to make game.
          }
        case None =>
          log.error(s"D2CS ReceivedGameRequest: Could not find realm $realmName.")
          sender() ! ReceivedGameResponse(SidStartAdvEx3.RESULT_ALREADY_EXISTS) //Apparently BNET never responded to SID_STARTADVEX3 except for open BNET.
      }

    case GameInfoRequest(connectionInfo, gameName) =>
      realmConnections.get(connectionInfo.actor) match {
        case Some(info) =>
          info.gameCache.find { case (storedGameName, _) => storedGameName.equalsIgnoreCase(gameName) } match {
            case Some((_, accountName)) =>
              info.binaryHandler.get(accountName) match {
                case Some(actorRef) =>
                  actorRef ! ReceivedGameResponse(SidStartAdvEx3.RESULT_SUCCESS)
                case None =>
                  log.error(s"D2CS GameInfoRequest: No binary handler actor found for account: $accountName.")
              }
            case None =>
              log.error(s"D2CS GameInfoRequest: Game $gameName not found in gameCache, not sending GameInfoRequest.")
          }
      }
    case RemoveRealm(connectionInfo) =>
      if (realmConnections.contains(connectionInfo.actor)) {
        realmConnections.remove(connectionInfo.actor)
      }
  }

  override def preStart(): Unit = {
    log.debug(s"D2CSActor started: ${self}")
  }

  override def postStop(): Unit = {
    log.error("D2CSActor has terminated!")
    super.postStop()
  }
}