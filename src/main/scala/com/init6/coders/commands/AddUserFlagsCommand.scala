package com.init6.coders.commands

import com.init6.Constants.{ACCOUNT_NOT_EXIST, INVALID_FLAG}
import com.init6.channels.{Flags, User, UserError, UserInfo}
import com.init6.coders.binary.hash.BSHA1
import com.init6.db.DAO

object AddUserFlagsCommand {

  def apply(account: String, flags: String): Command = {
    DAO.getUser(account).fold[Command]({
      UserInfo(ACCOUNT_NOT_EXIST(account))
    })(_ =>
      Flags.parseAndValidateFlag(flags) match {
        case Some(validFlag) => AddUserFlagsCommand(account, validFlag)
        case None => UserInfo(INVALID_FLAG(flags)) // Handle invalid flags
      }
    )
  }

}

case class AddUserFlagsCommand(account: String, flags: Int) extends Command