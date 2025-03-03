package com.init6.channels

import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, Address, PoisonPill, Props}
import akka.pattern.ask
import akka.util.Timeout
import com.init6.Constants._
import com.init6.coders.Base64
import com.init6.coders.commands._
import com.init6.servers._
import com.init6.utils.FutureCollector.futureSeqToFutureCollector
import com.init6.utils.RealKeyedCaseInsensitiveHashMap
import com.init6.{Init6Component, Init6RemotingActor}

import scala.concurrent.Await
import scala.util.{Failure, Success, Try}


/**
 * Created by filip on 9/20/15.
 */
object ChannelsActor extends Init6Component {
  def apply() = system.actorOf(Props[ChannelsActor].withDispatcher(CHANNELS_DISPATCHER), INIT6_CHANNELS_PATH)
}

case object GetChannels extends Command
case class ChannelsAre(channels: Seq[(String, ActorRef)]) extends Command
case object GetChannelUsers extends Command
case class ReceivedChannelUsers(users: Seq[(ActorRef, User)], topic: TopicExchange) extends Command
case class ReceivedBannedUsers(names: Seq[(ActorRef, Seq[String])]) extends Command
case class ReceivedDesignatedActors(designatedActors: Seq[(ActorRef, ActorRef)]) extends Command
case class ReceivedChannel(channel: (String, ActorRef)) extends Command
case class UserAdded(actor: ActorRef, channel: String) extends Command
case object ChannelEmpty extends Command
case object ChannelNotEmpty extends Command
case class ChannelDeleted(name: String) extends Command
case object MrCleanChannelEraser extends Command
case class KillChannel(actor: ActorRef, channel: String) extends Command with Remotable

class ChannelsActor extends Init6RemotingActor {

  override val actorPath = INIT6_CHANNELS_PATH
  implicit val timeout = Timeout(1000, TimeUnit.MILLISECONDS)

  val channels = RealKeyedCaseInsensitiveHashMap[ActorRef]()

  private def sendGetChannels(address: Address): Unit = {
    import context.dispatcher

    remoteActorSelection(address).resolveOne(Timeout(2, TimeUnit.SECONDS).duration).onComplete {
      case Success(actor) =>
        actor ! GetChannels

      case Failure(ex) =>
        system.scheduler.scheduleOnce(Timeout(500, TimeUnit.MILLISECONDS).duration)(sendGetChannels(address))
    }
  }


  override protected def onServerAlive(address: Address) = {
    sendGetChannels(address)
  }

  override def receive: Receive = {
    case MrCleanChannelEraser =>
      val futureSeq = channels
        .values
        .map {
          case (_, actor) =>
            actor ? CheckSize
        }

      Try {
        import context.dispatcher

        Await.result(futureSeq.collectResults {
          case ChannelSize(actor, name, size) =>
            if (size == 0) {
              self ! KillChannel(actor, name)
            }
            None
        }, Timeout(1, TimeUnit.SECONDS).duration)
      }.getOrElse(log.error("Failed to clean channels due to timeout."))

    case KillChannel(actor, name) =>
      actor ? PoisonPill
      channels -= name

    case c@ GetChannels =>
      log.error(s"### $c $channels")
      if (isRemote()) {
        val remoteActor = sender()
        remoteActor ! ChannelsAre(channels.values.toSeq)
        if (!remoteActors.contains(remoteActorSelection(remoteActor.path.address))) {
          remoteActor ! GetChannels
          remoteActors += remoteActorSelection(remoteActor.path.address)
        }
      }

    case c@ ChannelsAre(remoteChannels) =>
      log.error(s"### $c")
      remoteChannels
        .foreach {
          case (name, actor) => getOrCreate(name)
        }

    case command @ UserSwitchedChat(actor, user, channel, connectionTimestamp) =>
      //println(getAcceptingUptime.toNanos + " - " + user.name)
      val userActor = sender()
      Try {
        Await.result(getOrCreate(channel) ? AddUser(actor, user, connectionTimestamp), timeout.duration) match {
          case reply: UserAddedToChannel =>
            if (!user.inChannel.equalsIgnoreCase(channel)) {
              channels.get(user.inChannel).foreach {
                case (_, oldChannelActor) =>
                  oldChannelActor.tell(RemUser(actor), self)
              }
            }
            // temp actor
            if (isLocal(userActor)) {
              userActor ! ChannelJoinResponse(UserChannel(reply.user, reply.channelName, reply.channelFlags, reply.channelActor, reply.channelSize))
              // real actor
              if (reply.topicExchange.topic.nonEmpty) {
                actor ! UserInfo(CHANNEL_TOPIC(reply.topicExchange.topic))
              }
              // special case for now - refactor later
              // !!!!!!!!!!!!!!!
              // NEED TO GIVE REMOTES THE CORRECT FLAGS FROM THIS SERVER!!!!
              remoteActors.foreach(_.tell(UserSwitchedChat(actor, reply.user.copy(inChannel = user.inChannel), channel, connectionTimestamp), userActor))
            }
          case reply: ChatEvent =>
            if (isLocal(userActor)) {
              userActor ! ChannelJoinResponse(reply)
            }
          case msg =>
            log.info("Unhandled ChannelsActor Channel response message {}", msg)
        }
      }.getOrElse({
          log.error("ChannelsActor getOrCreate timed out for {}", command)
          if (isLocal(userActor)) {
            userActor ! ChannelJoinResponse(UserError(CHANNEL_FAILED_TO_JOIN(channel)))
          }
      })

    case ChannelsCommand =>
      import context.dispatcher

      val replyActor = sender()

      channels
        .values
        .map {
          case (_, actor) => actor ? ChannelsCommand
        }
        .collectResults {
          case channelInfo @ ChannelInfo(name, size, topic, creationTime, operator) if size > 0 =>
            Some(channelInfo) // Ensure it returns a valid result
          case channelInfoVoid @ ChannelInfoVoid(name, size, creationTime) =>
            Some(channelInfoVoid) // Handle ChannelInfoVoid
          case _ => None // Catch-all case for anything else
        }
        .foreach(responses => {
          if (responses.nonEmpty) {
            val sortedResponses = responses.sortWith((c1, c2) => {
              if (c1.name.equalsIgnoreCase("the void")) {
                false // c1 is "the void", so it should come last
              } else if (c2.name.equalsIgnoreCase("the void")) {
                true // c2 is "the void", so it should come last
              } else if (c1.creationTime == 0 && c2.creationTime == 0) {
                false // If both have creationTime 0, maintain order
              } else if (c1.creationTime == 0) {
                false // If c1 has creationTime 0, it comes after c2
              } else if (c2.creationTime == 0) {
                true // If c2 has creationTime 0, c1 comes before c2
              } else {
                c2.creationTime > c1.creationTime // Sort by creationTime in descending order
              }
            })

            replyActor ! UserInfo(CHANNEL_LIST(sortedResponses.size))
            sortedResponses.foreach {
              case ChannelInfo(name, size, topic, creationTime, operator) => replyActor ! UserInfo(CHANNEL_INFO(name, size, topic, creationTime, operator))
              case ChannelInfoVoid(name, size, creationTime) => replyActor ! UserInfo(CHANNEL_INFO_VOID(name, size, creationTime))
            }
          } else {
            replyActor ! UserInfo(CHANNEL_LIST_EMPTY)
          }
        })

    case WhoCommand(user, channel, opsOnly) =>
      getChannel(channel).fold(sender() ! UserErrorArray(CHANNEL_NOT_EXIST))(actor => {
        actor ! WhoCommandToChannel(sender(), user, opsOnly)
      })

    case c @ ShowChannelBans(channel) =>
      getChannel(channel).fold(sender() ! UserErrorArray(CHANNEL_NOT_EXIST))(actor => {
        actor.tell(c, sender())
      })

    case c @ PrintChannelUsers(channel) =>
      getChannel(channel).fold(sender() ! UserErrorArray(CHANNEL_NOT_EXIST))(actor => {
        actor.tell(c, sender())
      })

    // This is complete aids but never had a chance to fix it correctly.
    // .. Obviously need to get rid of this in the future. Puts too much strain on the outbound queue
    case c @ RemUser(actor) =>
      //println("##RemUser " + c + " - " + sender() + " - " + remoteActors)
      channels.values.map(_._2).foreach(_ ! c)
  }

  def getChannel(name: String): Option[ActorRef] = {
    channels.get(name).fold[Option[ActorRef]](None) {
      case (_, actor) => Some(actor)
    }
  }

  def getOrCreate(name: String) = {
    val fixedName = nameFixer(name)

    val channelActor = channels.getOrElse(fixedName, {
      val channelActor = context.actorOf(ChannelActor(fixedName).withDispatcher(CHANNEL_DISPATCHER), Base64(fixedName.toLowerCase))
      channels += fixedName -> channelActor
      fixedName -> channelActor
    })._2

    channelActor
  }

  def nameFixer(name: String) = {
    name.toLowerCase match {
      case "init 6" | "init6" => "init 6"
      case _ => name
    }
  }
}
