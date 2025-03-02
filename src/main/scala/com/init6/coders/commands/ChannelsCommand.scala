package com.init6.coders.commands

/**
  * Created by filip on 12/16/15.
  */
case object ChannelsCommand extends Command

trait ChannelListTraits {
  def name: String
  def creationTime: Long
}

//case class ChannelsCommand(actor: ActorRef) extends Command

case class ChannelInfo(name: String, size: Int, topic: String, creationTime: Long, operator: Array[String]) extends Command with ChannelListTraits
case class ChannelInfoVoid(name: String, size: Int, creationTime: Long) extends Command with ChannelListTraits
