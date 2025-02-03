package com.init6.coders.commands

import com.init6.channels.{Flags, User, UserError}

object UserFlagsCommand {

  def apply(user: User, message: String): Command = {
    if (Flags.isAdmin(user)) {
      val (account, flags) = CommandDecoder.spanBySpace(message)
      CloseAccountCommand(account, flags)
    } else {
      UserError()
    }
  }
}

case class UserFlagsCommand(account: String, flags: String)extends Command