package com.init6.realm

import akka.util.ByteString

object Statstring {

  def apply(clazz: Byte, flags: Byte): Statstring = {
    Statstring(
      Unknown_1           = 0x84.toByte,
      Unknown_2           = 0x80.toByte,
      Head                = 0xFF.toByte,
      Torso               = 0xFF.toByte,
      Legs                = 0xFF.toByte,
      RightArm            = 0xFF.toByte,
      LeftArm             = 0xFF.toByte,
      RightWeapon         = getRightWeapon(clazz),
      LeftWeapon          = 0xFF.toByte,
      Shield              = getShield(clazz),
      RightShoulder       = 0xFF.toByte,
      LeftShoulder        = 0xFF.toByte,
      LeftItem            = 0xFF.toByte,
      Type                = clazz,
      ColorHead           = 0xFF.toByte,
      ColorTorso          = 0xFF.toByte,
      ColorLegs           = 0xFF.toByte,
      ColorRightArm       = 0xFF.toByte,
      ColorLeftArm        = 0xFF.toByte,
      ColorRightWeapon    = 0xFF.toByte,
      ColorLeftWeapon     = 0xFF.toByte,
      ColorShield         = 0xFF.toByte,
      ColorRightShoulder  = 0xFF.toByte,
      ColorLeftShoulder   = 0xFF.toByte,
      ColorLeftItem       = 0xFF.toByte,
      Level               = 0x01.toByte, // 1
      Flags               = flags,
      Act                 = 0x80.toByte, // normal act 1
      Unknown_3           = 0xFF.toByte, // i think this field is documented incorrectly (0x80 = never logged in, 0xFF = has logged in)
      Unknown_4           = 0xFF.toByte, // i think this field is documented incorrectly (0x80 = never logged in, 0xFF = has logged in)
      Ladder              = flags,
      Unknown_5           = 0xFF.toByte,
      Unknown_6           = 0xFF.toByte
    )
  }

  def apply(bytes: ByteString): Unit = {
    Statstring(
      bytes(0),
      bytes(1),
      bytes(2),
      bytes(3),
      bytes(4),
      bytes(5),
      bytes(6),
      bytes(7),
      bytes(8),
      bytes(9),
      bytes(10),
      bytes(11),
      bytes(12),
      bytes(13),
      bytes(14),
      bytes(15),
      bytes(16),
      bytes(17),
      bytes(18),
      bytes(19),
      bytes(20),
      bytes(21),
      bytes(22),
      bytes(23),
      bytes(24),
      bytes(25),
      bytes(26),
      bytes(27),
      bytes(28),
      bytes(29),
      bytes(30),
      bytes(31),
      bytes(32),
    )
  }

  def getRightWeapon(clazz: Byte): Byte = {
    clazz match {
      case CharacterClass.Amazon => 0x1B
      case CharacterClass.Sorceress => 0x25
      case CharacterClass.Necromancer => 0x09
      case CharacterClass.Paladin => 0x11
      case CharacterClass.Barbarian => 0x04
      case CharacterClass.Druid => 0x0C
      case CharacterClass.Assassin => 0x2D
    }
  }

  def getShield(clazz: Byte): Byte = {
    clazz match {
      case CharacterClass.Amazon => 0x4F
      case CharacterClass.Sorceress => 0xFF.toByte
      case CharacterClass.Necromancer => 0xFF.toByte
      case CharacterClass.Paladin => 0x4F
      case CharacterClass.Barbarian => 0x4F
      case CharacterClass.Druid => 0x4F
      case CharacterClass.Assassin => 0x4F
    }
  }
}

case class Statstring(
  Unknown_1: Byte,
  Unknown_2: Byte,
  Head: Byte,
  Torso: Byte,
  Legs: Byte,
  RightArm: Byte,
  LeftArm: Byte,
  RightWeapon: Byte,
  LeftWeapon: Byte,
  Shield: Byte,
  RightShoulder: Byte,
  LeftShoulder: Byte,
  LeftItem: Byte,
  Type: Byte,
  ColorHead: Byte,
  ColorTorso: Byte,
  ColorLegs: Byte,
  ColorRightArm: Byte,
  ColorLeftArm: Byte,
  ColorRightWeapon: Byte,
  ColorLeftWeapon: Byte,
  ColorShield: Byte,
  ColorRightShoulder: Byte,
  ColorLeftShoulder: Byte,
  ColorLeftItem: Byte,
  Level: Byte,
  Flags: Byte,
  Act: Byte,
  Unknown_3: Byte,
  Unknown_4: Byte,
  Ladder: Byte,
  Unknown_5: Byte,
  Unknown_6: Byte,
) {

  def toBytes: ByteString = {
    ByteString(
      Unknown_1,
      Unknown_2,
      Head,
      Torso,
      Legs,
      RightArm,
      LeftArm,
      RightWeapon,
      LeftWeapon,
      Shield,
      RightShoulder,
      LeftShoulder,
      LeftItem,
      Type,
      ColorHead,
      ColorTorso,
      ColorLegs,
      ColorRightArm,
      ColorLeftArm,
      ColorRightWeapon,
      ColorLeftWeapon,
      ColorShield,
      ColorRightShoulder,
      ColorLeftShoulder,
      ColorLeftItem,
      Level,
      Flags,
      Act,
      Unknown_3,
      Unknown_4,
      Ladder,
      Unknown_5,
      Unknown_6
    )
  }
}
