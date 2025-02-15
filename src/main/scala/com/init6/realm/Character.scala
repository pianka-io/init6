package com.init6.realm


object CharacterClass {
  val Amazon = 0x01
  val Sorceress = 0x02
  val Necromancer = 0x03
  val Paladin = 0x04
  val Barbarian = 0x05
  val Druid = 0x06
  val Assassin = 0x07
}

object CharacterFlags {
  val Classic = 0x00
  val Hardcore = 0x04
  val Dead = 0x08
  val Expansion = 0x20
  val Ladder = 0x40
}

object LadderTypes {
  val NonLadder = 0xFF
  val Season1 = 0x01
}

case class Character(
  name: String,
  clazz: Int,
  flags: Int,
  ladder: Int,
  statstring: String
)
