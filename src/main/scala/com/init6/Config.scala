package com.init6

import java.io.File

import com.init6.coders.commands.Command
import com.typesafe.config.ConfigFactory

import scala.collection.JavaConverters._
import scala.util.Try

/**
  * Created by filip on 12/17/15.
  */
case object ReloadConfig extends Command

object Config {

  private var config: Config = reload()

  def apply() = {
    this.synchronized {
      config
    }
  }

  def reload() = {
    this.synchronized {
      config = new Config(sys.props("config"))
//      config = new Config("src/main/resources/init6.conf")
      config
    }
  }

  def load(filePath: String) = {
    val file = new File(filePath)
    if (file.exists()) {
      ConfigFactory.parseFile(file).resolve()
    } else {
      ConfigFactory.load(filePath)
    }
  }
}

sealed class Config(filePath: String) {

  val root = Config.load(filePath)
    .getConfig(Constants.INIT6)

  object Server {

    private val p = root.getConfig("server")

    val serverId = p.getInt("server-id")
    val host = p.getString("host")
    val ports = p.getIntList("ports").asScala
    val websocketPort = p.getInt("websocket-port")

    val akka_host = Try(p.getString("akka_host")).getOrElse(host)
    val akka_port = p.getInt("akka_port")
    val allNodes = p.getStringList("nodes").asScala
    val remoteNodes = allNodes.filterNot(_ == s"$akka_host:$akka_port")

    object Registry {

      private val pA = p.getConfig("registry")

      val initialDelay = pA.getInt("initial-delay")
      val pingDelay = pA.getInt("ping-delay")
      val dropAfter = pA.getInt("drop-after")
    }

    val reconThreshold = p.getInt("recon-threshold")

    object Chat {

      private val pA = p.getConfig("chat")

      val enabled = Try(pA.getBoolean("enabled")).getOrElse(false)
      val channels = Try(pA.getStringList("channels").asScala.toSet).getOrElse(Set.empty)
    }
  }

  object Channels {

    private val p = root.getConfig("channels")

    val limit = p.getInt("limit")
  }

  object Accounts {

    private val p = root.getConfig("accounts")

    val connectionLimit = p.getInt("connection-limit")
    val loginLimit = p.getInt("login-limit")

    val allowedCharacters =
      s"abcdefghijklmnopqrstuvwxyz0123456789${p.getString("allowed-illegal-characters")}".toSet

    val minLength = p.getInt("min-length")
    val maxLength = p.getInt("max-length")

    val enableMultipleLogins = p.getBoolean("enable-multiple")

    val enableIpWhitelist = p.getBoolean("enable-ip-whitelist")
    val ipWhitelist = p.getStringList("ip-whitelist").asScala.toSet
  }

  object Database {

    private val p = root.getConfig("database")

    val host = p.getString("host")
    val port = p.getInt("port")
    val username = p.getString("username")
    val password = p.getString("password")

    val batchUpdateInterval = p.getInt("batch-update-interval")
  }

  object Realm {
    private val p = root.getConfig("realm")

    val ipAddress = p.getString("ip_address")
  }

  object AntiFlood {

    private val p = root.getConfig("anti-flood")

    val enabled = p.getBoolean("enabled")
    val maxCredits = p.getInt("max-credits")
    val packetMinCost = p.getInt("packet-min-cost")
    val packetMaxCost = p.getInt("packet-max-cost")
    val costPerByte = p.getInt("cost-per-byte")
    val creditsReturnedPerSecond = p.getInt("credits-returned-per-second")

    val whitelist = p.getStringList("whitelist").asScala.toSet

    val ipBanTime = p.getInt("ipban-time")

    object ReconnectLimit {

      private val pA = p.getConfig("reconnect-limit")

      val enabled = pA.getBoolean("enabled")
      val ignoreAtStartFor = pA.getInt("ignore-at-start-for")
      val times = pA.getInt("times")
      val inPeriod = pA.getInt("in-period")
      val ipBanTime = pA.getInt("ipban-time")
    }
  }

  val motd = root.getStringList("motd")
    .asScala
    .map(line => {
      line
        .replaceAll("\\$buildNumber", BuildInfo.BUILD_NUMBER)
        .replaceAll("\\$buildHash", BuildInfo.BUILD_HASH)
    })
    .toArray
}
