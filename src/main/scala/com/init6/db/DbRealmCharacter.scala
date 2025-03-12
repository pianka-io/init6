package com.init6.db

/**
 * Created by pianka on 02/14/25.
 */
case class DbRealmCharacter(
  id: Int,
  userId: Long,
  name: String,
  `class`: Int,
  flags: Int,
  statstring: Array[Byte],
)
