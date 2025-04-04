package com.init6.users

import java.util.concurrent.TimeUnit
import akka.actor.{ActorRef, PoisonPill, Props, Terminated}
import akka.io.Tcp.{Abort, Received, ResumeAccepting}
import akka.pattern.ask
import akka.util.{ByteString, Timeout}
import com.init6.Constants._
import com.init6.channels._
import com.init6.channels.utils.ChannelJoinValidator
import com.init6.coders._
import com.init6.coders.binary.{BinaryChatEncoder, BinaryChatEncoderDiablo}
import com.init6.coders.chat1.Chat1Encoder
import com.init6.coders.commands._
import com.init6.coders.telnet._
import com.init6.connection.{ConnectionInfo, IpBan, WriteOut}
import com.init6.db._
import com.init6.servers.RepeatingAnnoucement
import com.init6.utils.FutureCollector.futureSeqToFutureCollector
import com.init6.utils.{CaseInsensitiveHashSet, ChatValidator}
import com.init6.{Config, Init6Actor, Init6LoggingActor, ReloadConfig}

import scala.concurrent.Await

/**
 * Created by filip on 9/27/15.
 */
object UserActor {
  def apply(connectionInfo: ConnectionInfo, user: User, protocol: Protocol) =
    Props(classOf[UserActor], connectionInfo, user,
      protocol match {
        case BinaryProtocol =>
          user.client match {
            case "VD2D" => BinaryChatEncoderDiablo
            case "PX2D" => BinaryChatEncoderDiablo
            case _ => BinaryChatEncoder
          }
        case TelnetProtocol => TelnetEncoder
        case Chat1Protocol => Chat1Encoder
      }
    )
}

case class JoinChannelFromConnection(channel: String, forceJoin: Boolean)
case class UserUpdated(user: User) extends ChatEvent
case class PingSent(time: Long, cookie: String) extends Command
case class UpdatePing(ping: Int) extends Command
case object KillConnection extends Command
case class DisconnectOnIp(ipAddress: Array[Byte]) extends Command
case class AddUserFlags(flags: Int) extends Command
case class RemUserFlags(flags: Int) extends Command

class UserActor(connectionInfo: ConnectionInfo, var user: User, var encoder: Encoder)
  extends FloodPreventer with Init6Actor with Init6LoggingActor {

  import context.dispatcher

  var isTerminated = false
  var channelActor = ActorRef.noSender
  var squelchedUsers = CaseInsensitiveHashSet()
  var friendsList: Option[Seq[DbFriend]] = None
  var replyToUser: Option[String] = None
  val awayAvailablity = AwayAvailablity(user.name)
  val dndAvailablity = DndAvailablity(user.name)

  var pingTime: Long = 0
  var pingCookie: String = ""
  val connectedTime = System.currentTimeMillis()

  override def preStart() = {
    super.preStart()

    context.watch(connectionInfo.actor)
  }

  def checkSquelched(user: User) = {
    if (squelchedUsers.contains(user.name)) {
      Flags.squelch(user)
    } else {
      Flags.unsquelch(user)
    }
  }

  def encodeAndSend(chatEvent: ChatEvent) = {
    encoder(chatEvent).foreach(message => connectionInfo.actor ! WriteOut(message))
  }

  override def loggedReceive: Receive = {
    case SetCharacter(username, character, statstring) =>
      user.character = Some(character)
      val fullStatstring = {
        user.client.getBytes() ++
        user.realm.getOrElse("Unknown").getBytes() ++
        Array(0x2C.toByte) ++
        character.getBytes() ++
        Array(0x2C.toByte) ++
        statstring
      }
      user.statstring = Some(ByteString(fullStatstring))

    case SetRealm(username, realm) =>
      user.realm = Some(realm)

    case JoinGame(username, game) =>
      user.inGame = Some(game)
      if (user.inChannel != "") channelActor ! RemUser(self)

    case JoinChannelFromConnection(channel, forceJoin) =>
      log.debug("matched JoinChannelFromConnection")
      joinChannel(channel, forceJoin)
      connectionInfo.actor ! ResumeAccepting(1)

    case ChannelToUserPing =>
      log.debug("matched ChannelToUserPing")
      sender() ! UserToChannelPing

    // From Users Actor
    case UsersUserAdded(userActor, newUser) =>
      log.debug("matched UsersUserAdded")
      if (self != userActor && user.name.equalsIgnoreCase(newUser.name)) {
        // This user is a stale connection!
        self ! KillConnection
      }

    case GetUptime =>
      log.debug("matched GetUptime")
      sender() ! ReceivedUptime(self, connectedTime)

    case PingSent(time, cookie) =>
      log.debug("matched PingSent")
      pingTime = time
      pingCookie = cookie

    case PongCommand(cookie) =>
      log.debug("matched PongCommand")
      handlePingResponse(cookie)

    case ChannelJoinResponse(event) =>
      log.debug("matched ChannelJoinResponse")
      event match {
        case UserChannel(newUser, channel, flags, channelActor, channelSize) =>
          user = newUser
          this.channelActor = channelActor
          channelActor ! GetUsers
        case _ =>
      }
      encodeAndSend(event)

    case UserSquelched(username) =>
      log.debug("matched UserSquelched")
      squelchedUsers += username

    case UserUnsquelched(username) =>
      log.debug("matched UserUnsquelched")
      squelchedUsers -= username

    case AddUserFlags(flags) =>
      user = Flags.adminAddFlags(user, flags)
      channelActor ! UpdateUserFlags(user)

    case RemUserFlags(flags) =>
      user = Flags.adminRemoveFlags(user, flags)
      channelActor ! UpdateUserFlags(user)

    case chatEvent: ChatEvent =>
      log.debug("matched ChatEvent")
      handleChatEvent(chatEvent)

    case (actor: ActorRef, WhisperMessage(fromUser, toUsername, message, sendNotification)) =>
      log.debug("matched WhisperMessage")
      encoder(UserWhisperedFrom(fromUser, message))
        .foreach(msg => {
          dndAvailablity
            .whisperAction(actor)
            .getOrElse({
              replyToUser = Some(fromUser.name)
              connectionInfo.actor ! WriteOut(msg)
              awayAvailablity.whisperAction(actor)
              if (sendNotification) {
                actor ! UserWhisperedTo(user, message)
              }
            })
        })

    case (actor: ActorRef, WhoisCommand(fromUser, username)) =>
      log.debug("matched WhoisCommand")
      actor !
        (if (Flags.isAdmin(fromUser)) {
          UserInfo(s"${user.name} (${user.ipAddress}) is using ${encodeClient(user.client)}${if (user.inChannel != "") s" in the channel ${user.inChannel}" else ""} on server ${Config().Server.host}.")
        } else {
          UserInfo(s"${user.name} is using ${encodeClient(user.client)}${if (user.inChannel != "") s" in the channel ${user.inChannel}" else ""} on server ${Config().Server.host}.")
        })

    case (actor: ActorRef, FriendsWhois(position, username)) =>
      log.debug("matched FriendsWhois")
      actor ! FriendsWhoisResponse(online = true, position, user.name, user.client, user.inChannel, Config().Server.host)

    case (actor: ActorRef, PlaceOfUserCommand(_, _)) =>
      log.debug("matched PlaceOfUserCommand")
      actor ! UserInfo(USER_PLACED(user.name, connectionInfo.place, Config().Server.host))

    case WhoCommandResponse(whoResponseMessage, userMessages) =>
      log.debug("matched WhoCommandResponse")
      whoResponseMessage.fold(encodeAndSend(UserErrorArray(CHANNEL_NOT_EXIST)))(whoResponseMessage => {
        encodeAndSend(UserInfo(whoResponseMessage))
        userMessages.foreach(userMessage => encodeAndSend(UserInfo(userMessage)))
      })

    case WhoCommandError(errorMessage) =>
      log.debug("matched WhoCommandError")
      encodeAndSend(UserError(errorMessage))

    case ShowBansResponse(chatEvent: ChatEvent) =>
      log.debug("matched ShowBansResponse")
      encodeAndSend(chatEvent)

    case PrintChannelUsersResponse(chatEvent: ChatEvent) =>
      log.debug("matched PrintChannelUsersResponse")
      encodeAndSend(chatEvent)

    case BanCommand(kicking, message) =>
      log.debug("matched BanCommand")
      self ! UserInfo(YOU_KICKED(kicking))
      joinChannel(THE_VOID)

    case KickCommand(kicking, message) =>
      log.debug("matched KickCommand")
      self ! UserInfo(YOU_KICKED(kicking))
      joinChannel(THE_VOID)

    case DAOCreatedAck(username, passwordHash) =>
      log.debug("matched DAOCreatedAck")
      self ! UserInfo(ACCOUNT_CREATED(username, passwordHash))

    case DAOUpdatedPasswordAck(username, passwordHash) =>
      log.debug("matched DAOUpdatedPasswordAck")
      self ! UserInfo(ACCOUNT_UPDATED(username, passwordHash))

    case DAOUpdateAccountFlagsAck(username, flags) =>
      log.debug("matched DAOUpdateAccountFlagsAck")
      self ! UserInfo(ACCOUNT_FLAGS_UPDATED(username, flags))

    case DAOClosedAccountAck(username, reason) =>
      log.debug("matched DAOClosedAccountAck")
      self ! UserInfo(ACCOUNT_CLOSED(username, reason))

    case DAOOpenedAccountAck(username) =>
      log.debug("matched DAOOpenedAccountAck")
      self ! UserInfo(ACCOUNT_OPENED(username))

    case DAOAliasCommandAck(aliasTo) =>
      log.debug("matched DAOAliasCommandAck")
      self ! UserInfo(ACCOUNT_ALIASED(aliasTo))

    case DAOAliasToCommandAck(aliasTo) =>
      log.debug("matched DAOAliasToCommandAck")
      self ! UserInfo(ACCOUNT_ALIASED_TO(aliasTo))

    case DAOFriendsAddResponse(friendsList, friend) =>
      log.debug("matched DAOFriendsAddResponse")
      this.friendsList = Some(friendsList)
      self ! UserInfo(FRIENDS_ADDED_FRIEND(friend.friend_name))

    case DAOFriendsListToListResponse(friendsList) =>
      log.debug("matched DAOFriendsListToListResponse")
      this.friendsList = Some(friendsList)
      sendFriendsList(friendsList)

    case DAOFriendsListToMsgResponse(friendsList, msg) =>
      log.debug("matched DAOFriendsListToMsgResponse")
      this.friendsList = Some(friendsList)
      sendFriendsMsg(friendsList, msg)

    case DAOFriendsRemoveResponse(friendsList, friend) =>
      log.debug("matched DAOFriendsRemoveResponse")
      this.friendsList = Some(friendsList)
      self ! UserInfo(FRIENDS_REMOVED_FRIEND(friend.friend_name))

    case ReloadDbAck =>
      log.debug("matched ReloadDbAck")
      self ! UserInfo(s"$INIT6_SPACE database reloaded.")

    case command: FriendsCommand =>
      log.debug("matched FriendsCommand")
      handleFriendsCommand(command)

    // THIS SHIT NEEDS TO BE REFACTORED!
    case Received(data) =>
      log.debug("matched Received")
      // sanity check
      if (!ChatValidator(data)) {
        connectionInfo.actor ! Abort
        self ! KillConnection
      } else {
        val command = CommandDecoder(user, data)
        if (Config().AntiFlood.enabled && floodState(command, data.length)) {
          // Handle AntiFlood
          encodeAndSend(UserFlooded)
          ipLimiterActor ! IpBan(
            connectionInfo.ipAddress.getAddress.getAddress,
            System.currentTimeMillis + (Config().AntiFlood.ipBanTime * 1000)
          )
          self ! KillConnection
        } else {
          //log.error(s"UserMessageDecoder $command")
          command match {
            case PongCommand(cookie) =>
              handlePingResponse(cookie)
            /**
             * The channel command and user command have two different flows.
             * A user has to go through a middle-man users actor because there is no guarantee the receiving user is online.
             * A command being sent to the user's channel can be done via actor selection, since we can guarantee the
             * channel exists.
             */
            case c@JoinUserCommand(fromUser, channel) =>
              if (ChannelJoinValidator(user.inChannel, channel)) {
                joinChannel(channel)
              }
            case ResignCommand => resign()
            case RejoinCommand => rejoin()
            case command: ChannelCommand =>
              if (channelActor != ActorRef.noSender) {
                channelActor ! command
              }
            case ChannelsCommand => channelsActor ! ChannelsCommand
            case command: ShowChannelBans => channelsActor ! command
            case command: WhoCommand => channelsActor ! command
            case command: PrintChannelUsers => channelsActor ! command
            case command: OperableCommand =>
              if (Flags.canBan(user)) {
                usersActor ! command
              } else {
                encoder(UserError(NOT_OPERATOR)).foreach(connectionInfo.actor ! WriteOut(_))
              }
            case command: UserToChannelCommand => usersActor ! command
            case command: UserCommand => usersActor ! command
            case command: ReturnableCommand => encoder(command).foreach(connectionInfo.actor ! WriteOut(_))
            case command@UsersCommand => usersActor ! command
            case command@UptimeCommand => usersActor ! command
            case command: TopCommand =>
              if (command.serverIp != Config().Server.host) {
                system.actorSelection(remoteAddress(command.serverIp, INIT6_TOP_COMMAND_PATH)) ! command
              } else {
                topCommandActor ! command
              }
            case command: GetRankingCommand =>
              if (command.serverIp != Config().Server.host) {
                system.actorSelection(remoteAddress(command.serverIp, INIT6_RANKING_PATH)) ! command
              } else {
                rankingActor ! command
              }
            case PlaceOfSelfCommand => encodeAndSend(UserInfo(PLACED(connectionInfo.place, Config().Server.host)))
            case command@PlaceOnServerCommand(serverIp) =>
              if (command.serverIp != Config().Server.host) {
                system.actorSelection(remoteAddress(command.serverIp, INIT6_USERS_PATH)) ! command
              } else {
                encodeAndSend(UserInfo(SERVER_PLACE(getPlace, Config().Server.host)))
              }
            case AwayCommand(message) => awayAvailablity.enableAction(message)
            case DndCommand(message) => dndAvailablity.enableAction(message)
            case ReplyCommand(message) =>
              replyToUser.fold({
                encodeAndSend(UserError(NO_WHISPER_USER_INPUT))
              })(replyToUser => {
                usersActor ! WhisperMessage(user, replyToUser, message, sendNotification = true)
              })
            case AccountMade(username, passwordHash) =>
              daoActor ! CreateAccount(username, passwordHash)
            case ChangePasswordCommand(newPassword) =>
              daoActor ! UpdateAccountPassword(user.name, newPassword)
            case AliasCommand(alias) =>
              daoActor ! DAOAliasCommand(user, alias)
            case AliasToCommand(alias) =>
              daoActor ! DAOAliasToCommand(user, alias)

            case command: FriendsCommand =>
              handleFriendsCommand(command)
            //ADMIN
            case command@BroadcastCommand(message) =>
              usersActor ! command
            case command@DisconnectCommand(user) =>
              usersActor ! command
            case command@IpBanCommand(ipAddress, until) =>
              ipLimiterActor ! IpBan(ipAddress, until)
              usersActor ! command
            case command@UnIpBanCommand(ipAddress) =>
              ipLimiterActor ! command
            case command@CloseAccountCommand(account, reason) =>
              daoActor ! CloseAccount(account, reason)
            case command@OpenAccountCommand(account) =>
              daoActor ! OpenAccount(account)
            case command@SetAccountPasswordCommand(account, newPassword) =>
              daoActor ! UpdateAccountPassword(account, newPassword)
            case command@AddUserFlagsCommand(account, flags) =>
              usersActor ! command
              daoActor ! AddAccountFlags(account, flags)
            case command@RemoveUserFlagsCommand(account, flags) =>
              usersActor ! command
              //cant send the new flag...
              daoActor ! RemoveAccountFlags(account, flags)

              //Change DB and update channel... daoActor for update? and also channelActor?
            case ReloadConfig =>
              Config.reload()
              self ! UserInfo(s"$INIT6_SPACE configuration reloaded.")
            case ReloadDb =>
              daoActor ! ReloadDb
            case PrintConnectionLimit =>
              ipLimiterActor ! PrintConnectionLimit
            case PrintLoginLimit =>
              usersActor ! PrintLoginLimit
            case c: RepeatingAnnoucement =>
              serverAnnouncementActor ! c
            case _ =>
          }
        }
      }

    case command: UserToChannelCommandAck =>
      log.debug("matched UserToChannelCommandAck")
      //log.error(s"UTCCA $command")
      if (channelActor != ActorRef.noSender) {
        //println("Sending to channel UTCCA " + command)
        channelActor ! command
      }

    case Terminated(actor) =>
      log.debug("matched Terminated")
      //println("#TERMINATED " + sender() + " - " + self + " - " + user)
      // CAN'T DO THIS - channelActor msg might be faster than channelSActor join msg. might remove itself then add after
//      if (channelActor != ActorRef.noSender) {
//        channelActor ! RemUser(self)
//      } else {
        channelsActor ! RemUser(self)
//      }
      usersActor ! Rem(connectionInfo.ipAddress, self)
      self ! PoisonPill

    case KillConnection =>
      log.debug("matched KillConnection")
      //println("#KILLCONNECTION FROM " + sender() + " - FOR: " + self + " - " + user)
      connectionInfo.actor ! PoisonPill

    case DisconnectOnIp(ipAddress) =>
      log.debug("matched DisconnectOnIp")
      if (!Flags.isAdmin(user) && this.connectionInfo.ipAddress.getAddress.getAddress.sameElements(ipAddress)) {
        connectionInfo.actor ! PoisonPill
      }

    case x =>
      log.debug("{} UserActor Unhandled {}", user.name, x)
  }

  private def handleChatEvent(chatEvent: ChatEvent) = {
    log.debug(s"handling chat event $chatEvent")
    val chatEventSender = sender()
    // If it comes from Channels/*, make sure it is the current channelActor
    log.debug(s"path parent name ${chatEventSender.path.parent.name}")
    log.debug(s"chat event sender $chatEventSender")
    if (chatEventSender.path.parent.name != INIT6_CHANNELS_PATH || chatEventSender == channelActor) {
      log.debug("entering handleChatEvent handling")
      chatEvent match {
        case UserUpdated(newUser) =>
          log.debug("UserUpdated")
          user = newUser

        case UserIn(user) =>
          log.debug("UserIn")
          encodeAndSend(UserIn(checkSquelched(user)))

        case UserJoined(user) =>
          log.debug("UserJoined")
          encodeAndSend(UserJoined(checkSquelched(user)))

        case UserFlags(user) =>
          log.debug("UserFlags")
          encodeAndSend(UserFlags(checkSquelched(user)))

        case channelEvent: SquelchableTalkEvent =>
          log.debug("SquelchableTalkEvent")
          if (!squelchedUsers.contains(channelEvent.user.name)) {
            encodeAndSend(channelEvent)
          }

        case UserWhisperedTo(user, message) =>
          log.debug("UserWhisperedTo")
          replyToUser = Some(user.name)
          encodeAndSend(chatEvent)

        case _ =>
          log.debug("catch all sending")
          encodeAndSend(chatEvent)
      }
    } else {
      log.debug("skipping handleChatEvent handling")
    }
  }

  private def handleFriendsCommand(friendsCommand: FriendsCommand): Unit = {
    if (user.id == 0) {
//      encodeAndSend(UserError("Due to a pending fix, new accounts cannot use the friends list until the next server restart."))
      return
    }

    friendsCommand match {
      case FriendsAdd(who) =>
        if (user.name.equalsIgnoreCase(who)) {
          encodeAndSend(UserError(FRIENDS_ADD_NO_YOURSELF))
        } else {
          daoActor ! DAOFriendsAdd(user.id, who)
        }
      case FriendsDemote(who) =>
        encodeAndSend(UserError("/friends demote is not yet implemented."))
      case FriendsList() =>
        friendsList.fold(daoActor ! DAOFriendsListToList(user.id))(sendFriendsList)
      case FriendsMsg(msg) =>
        friendsList.fold(daoActor ! DAOFriendsListToMsg(user.id, msg))(friendsList => sendFriendsMsg(friendsList, msg))
      case FriendsPromote(who) =>
        encodeAndSend(UserError("/friends promote is not yet implemented."))
      case FriendsRemove(who) =>
        daoActor ! DAOFriendsRemove(user.id, who)
    }
  }

  private def sendFriendsList(friendsList: Seq[DbFriend]) = {
    if (friendsList.nonEmpty) {
      implicit val timeout = Timeout(1000, TimeUnit.MILLISECONDS)
      friendsList
        .map(friend => usersActor ? FriendsWhois(friend.friend_position, friend.friend_name)).collectResults {
        case c: FriendsWhoisResponse => Some(c)
      }
        .foreach(friendResponses => {
          val sortedFriends = friendResponses.sortBy(_.position)

          val replies = FRIENDS_HEADER +: sortedFriends.map(response => {
            if (response.online) {
              FRIENDS_FRIEND_ONLINE(response.position, response.username, encodeClient(response.client), response.channel, response.server)
            } else {
              FRIENDS_FRIEND_OFFLINE(response.position, response.username)
            }
          })
            .toArray

          encodeAndSend(UserInfoArray(replies))
        })
    } else {
      encodeAndSend(UserError(FRIENDS_LIST_NO_FRIENDS))
    }
  }

  private def sendFriendsMsg(friendsList: Seq[DbFriend], msg: String) = {
    usersActor ! WhisperToFriendsMessage(user, friendsList.map(_.friend_name), msg)
  }

  private def handlePingResponse(cookie: String) = {
    if (channelActor != ActorRef.noSender && pingCookie == cookie) {
      val updatedPing = Math.max(0, System.currentTimeMillis() - pingTime).toInt
      if (updatedPing <= 60000) {
        channelActor ! UpdatePing(updatedPing)
      }
    }
  }

  private def resign() = {
    if (Flags.isOp(user)) {
      rejoin()
    }
  }

  private def rejoin(): Unit = {
    joinChannel(user.inChannel)
  }

  //var joiningTime: Long = -1
  private def joinChannel(channel: String, forceJoin: Boolean = false): Unit = {
    val sanitizedChannel = channel.replaceAll("\\s+", " ").trim
    if (sanitizedChannel.isEmpty) {
      return
    }
    if (Flags.isAdmin(user) ||
      !Config().Server.Chat.enabled ||
      Config().Server.Chat.channels.map(_.toLowerCase).contains(sanitizedChannel.toLowerCase)
    ) {
      implicit val timeout = Timeout(2, TimeUnit.SECONDS)
      //println(user.name + " - " + self + " - SENDING JOIN")
      //if (joiningTime == -1) joiningTime = getAcceptingUptime.toNanos
      Await.result(channelsActor ? UserSwitchedChat(self, user, sanitizedChannel, connectionInfo.connectedTime), timeout.duration) match {
        case ChannelJoinResponse(event) =>
          //println(user.name + " - " + self + " - RECEIVED JOIN")
          event match {
            case UserChannel(newUser, channel, flags, channelActor, channelSize) =>
              user = newUser
              this.channelActor = channelActor
              channelActor ! GetUsers

              topCommandActor ! UserChannelJoined(connectionInfo, user, channelSize)
            case _ =>
              // Seems best for most poopylicious bots that enjoy getting stuck in limbo
              // Basically throw to void on force join of channel is full/or is banned
              if (forceJoin && !user.inChannel.equalsIgnoreCase(THE_VOID)) {
                self ! JoinChannelFromConnection(THE_VOID, forceJoin)
              }
          }
          encodeAndSend(event)
      }
    } else {
      encodeAndSend(UserError(CHANNEL_RESTRICTED))
    }
  }
}
