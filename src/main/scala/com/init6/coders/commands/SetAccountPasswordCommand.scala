package com.init6.coders.commands

import com.init6.Constants._
import com.init6.channels.UserInfo
import com.init6.coders.binary.hash.BSHA1
import com.init6.db.DAO

/**
  * Created by Chris on 02/14/25.
  */
object SetAccountPasswordCommand {

  def apply(account: String, newPassword: String): Command = {

    DAO.getUser(account).fold[Command]({
      UserInfo(ACCOUNT_NOT_EXIST(account))
    })(_ => if(newPassword.nonEmpty) {
      SetAccountPasswordCommand(account, BSHA1(newPassword))
    } else {
      UserInfo(NO_PASSWORD_INPUT)
    })
  }
}

case class SetAccountPasswordCommand(account: String, passwordHash: Array[Byte]) extends Command
