package com.init6.realm

/**
 * Created by pianka on 03/01/25.
 */
case class Game(
  name: String,
  password: String,
  description: String,
  players: List[com.init6.realm.Character]
)
