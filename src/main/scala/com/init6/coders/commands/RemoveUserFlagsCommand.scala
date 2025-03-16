package com.init6.coders.commands

import com.init6.Constants.{ACCOUNT_NOT_EXIST, INVALID_FLAG}
import com.init6.channels.{Flags, UserInfo}
import com.init6.db.DAO

object RemoveUserFlagsCommand {

  def apply(account: String, flags: String): Command = {
    DAO.getUser(account).fold[Command]({
      UserInfo(ACCOUNT_NOT_EXIST(account))
    })(_ =>
      Flags.parseAndValidateFlag(flags) match {
        case Some(validFlag) => RemoveUserFlagsCommand(account, validFlag)
        case None => UserInfo(INVALID_FLAG(flags)) // Handle invalid flags
      }
    )
  }

}

case class RemoveUserFlagsCommand(account: String, flags: Int) extends Command