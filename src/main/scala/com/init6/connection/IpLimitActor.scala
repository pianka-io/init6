package com.init6.connection

import akka.actor.{ActorRef, Props}
import com.init6.Constants._
import com.init6.channels.{UserInfo, UserInfoArray}
import com.init6.coders.IPUtils
import com.init6.coders.commands.{PrintConnectionLimit, UnIpBanCommand}
import com.init6.{Config, Init6Component, Init6RemotingActor}
import sys.process._
import scala.collection.mutable

/**
  * Created by filip on 1/9/16.
  */
object IpLimitActor extends Init6Component {
  def apply(limit: Int) = system.actorOf(Props(classOf[IpLimitActor], limit), INIT6_IP_LIMITER_PATH)
}

case class Connected(connectionInfo: ConnectionInfo)
case class Disconnected(connectingActor: ActorRef)
case class Allowed(connectionInfo: ConnectionInfo)
case class NotAllowed(connectionInfo: ConnectionInfo)
case class IpBan(address: Array[Byte], until: Long)

class IpLimitActor(limit: Int) extends Init6RemotingActor {

  override val actorPath = INIT6_IP_LIMITER_PATH

  val actorToIp = mutable.HashMap.empty[ActorRef, Int]
  val ipCount = mutable.HashMap.empty[Int, Int]
  val ipTotalCount = mutable.HashMap.empty[Int, Int]
  val ipBanned = mutable.HashMap.empty[Int, Long]

  def addIpConnection(addressInt: Int) = {
    val t = System.currentTimeMillis

    if (Config().AntiFlood.ReconnectLimit.enabled &&
      getAcceptingUptime.toSeconds >= Config().AntiFlood.ReconnectLimit.ignoreAtStartFor) {
      val totalCount = ipTotalCount.getOrElse(addressInt, 0) + 1
      ipTotalCount.update(addressInt, totalCount)
      totalCount <= 1000
    } else {
      log.info("NOT ENABLED")
      true
    }
  }

  override def receive: Receive = {
    case Connected(connectionInfo) =>
      if (
        Config().Accounts.enableIpWhitelist &&
        !Config().Accounts.ipWhitelist.contains(connectionInfo.ipAddress.getAddress.getHostAddress)
      ) {
        sender() ! NotAllowed(connectionInfo)
      } else {
        val address = connectionInfo.ipAddress.getAddress.getHostAddress
        val addressInt = IPUtils.bytesToDword(connectionInfo.ipAddress.getAddress.getAddress)
        val current = ipCount.getOrElse(addressInt, 0)
        if (!addIpConnection(addressInt)) {
          ipBanned += addressInt -> (System.currentTimeMillis() + (Config().AntiFlood.ReconnectLimit.ipBanTime * 1000))
          s"sudo nft add rule inet filter input ip saddr ${address} drop".!
        }
        val isIpBanned = ipBanned.get(addressInt).exists(until => {
          if (System.currentTimeMillis >= until) {
            ipBanned -= addressInt
            false
          } else {
            true
          }
        })

        if (limit > current && !isIpBanned) {
          actorToIp += connectionInfo.actor -> addressInt
          ipCount += addressInt -> (current + 1)
          sender() ! Allowed(connectionInfo)
        } else {
          sender() ! NotAllowed(connectionInfo)
        }
      }

    case Disconnected(connectingActor) =>
      actorToIp
        .get(connectingActor)
        .foreach(addressInt => {
          val current = ipCount.getOrElse(addressInt, 0)
          if (current > 0) {
            // Should always get through the if though...
            ipCount += addressInt -> (current - 1)
          }
          actorToIp -= connectingActor
        })

    case IpBan(address, until) =>
      val addressInt = IPUtils.bytesToDword(address)
      ipBanned += addressInt -> until
      sender() ! UserInfo(IPBANNED(IPUtils.dwordToString(addressInt)))

    case UnIpBanCommand(address) =>
      val addressInt = IPUtils.bytesToDword(address)
      ipBanned -= addressInt
      sender() ! UserInfo(UNIPBANNED(IPUtils.dwordToString(addressInt)))

    case PrintConnectionLimit =>
      sender() ! UserInfoArray(
        ipCount.map {
          case (ipDword, count) =>
            s"${IPUtils.dwordToString(ipDword)} - $count"
        }.toArray
      )
  }
}
