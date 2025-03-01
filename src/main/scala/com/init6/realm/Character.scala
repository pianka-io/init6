package com.init6.realm

import akka.util.ByteString
import com.init6.db.DbRealmCharacter
import com.init6.realm.CharacterFlags.Ladder
import com.init6.realm.LadderTypes.{NonLadder, Season1}


object CharacterClass {
  val Amazon = 0x00
  val Sorceress = 0x01
  val Necromancer = 0x02
  val Paladin = 0x03
  val Barbarian = 0x04
  val Druid = 0x05
  val Assassin = 0x06
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
  statstring: ByteString
)

object Character {

  def apply(character: DbRealmCharacter): Character = {
    Character(
      character.name,
      character.`class`,
      character.flags,
      if ((character.flags & Ladder) == Ladder) Season1 else NonLadder,
      ByteString(character.statstring)
    )
  }
}