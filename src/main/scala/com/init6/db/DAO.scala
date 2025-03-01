package com.init6.db

import akka.util.ByteString
import com.init6.Config
import com.init6.realm.{Character, Statstring}
import scalikejdbc._

/**
 * Created by filip on 9/20/15.
 */
object DAO {

  GlobalSettings.loggingSQLAndTime = GlobalSettings.loggingSQLAndTime.copy(enabled = false)
  Class.forName("org.mariadb.jdbc.Driver")
  ConnectionPool.singleton(
    s"jdbc:mariadb://${Config().Database.host}:${Config().Database.port}/vilenet", Config().Database.username, Config().Database.password
  )
  implicit val session = AutoSession

  var userCache: UserCache = _
  var friendsCache: FriendsListCache = _
  reloadCache()

  implicit def decodeFlags(flags: Array[Byte]): Int = flags(0) << 24 | flags(1) << 16 | flags(2) << 8 | flags(3)

  def reloadCache() = {
    if (userCache != null) {
      userCache.close()
    }
    val dbUser = DbUser.syntax("user")
    val dbFriendsList = DbFriendsList.syntax("friend")

    userCache = new UserCache(withSQL {
      select.from(DbUser as dbUser)
    }.map(DbUser(_)).list().apply())

    friendsCache = new FriendsListCache(withSQL {
      select.from(DbFriendsList as dbFriendsList)
    }.map(DbFriendsList(_)).list().apply())
  }

  def close() = {
    userCache.close()
    session.close()
  }

  object DbRealmCookie extends SQLSyntaxSupport[DbRealmCookie] {
    override val tableName = "realm_cookies"

    def apply(rs: WrappedResultSet) = new DbRealmCookie(
      rs.int(1),
      rs.long(2)
    )
  }

  private[db] def createRealmCookie(userId: Long) = {
    DB localTx { implicit session =>
      withSQL {
        insertInto(DbRealmCookie)
          .values(
            None,
            userId
          )
      }
      .updateAndReturnGeneratedKey()
      .apply()
    }
  }

  private[db] def realmReadCookie(cookie: Int): Option[DbRealmCookie] = {
    DB readOnly { implicit session =>
      val c = DbRealmCookie.syntax("c")

      withSQL {
        select(c.result.id, c.result.userId)
          .from(DbRealmCookie as c)
          .where.eq(c.id, cookie)
      }
      .map(rs => com.init6.db.DbRealmCookie(rs.int(c.resultName.id), rs.long(c.resultName.userId)))
      .single()
      .apply()
    }
  }

  object DbRealmCharacter extends SQLSyntaxSupport[DbRealmCharacter] {
    override val tableName = "realm_characters"

    def apply(rs: WrappedResultSet) = new DbRealmCharacter(
      rs.int(1),
      rs.long(2),
      rs.string(3),
      rs.int(4),
      rs.int(5),
      rs.bytes(6)
    )
  }

  private[db] def createCharacter(userId: Long, name: String, clazz: Int, flags: Int, statstring: Statstring): Unit = {
    DB localTx { implicit session =>
      withSQL {
        insertInto(DbRealmCharacter)
          .values(
            None,
            userId,
            name,
            clazz,
            flags,
            statstring.toBytes.toArray
          )
      }
      .updateAndReturnGeneratedKey()
      .apply()
    }
  }

  private[db] def readCharacters(userId: Long): List[Character] = {
    val c = DbRealmCharacter.syntax("c")

    withSQL {
      select
        .from(DbRealmCharacter as c)
        .where.eq(c.userId, userId)
    }
    .map(rs => com.init6.db.DbRealmCharacter(
      rs.int(c.resultName.id),
      rs.long(c.resultName.userId),
      rs.string(c.resultName.name),
      rs.int(c.resultName.`class`),
      rs.int(c.resultName.flags),
      rs.bytes(c.resultName.statstring)
    ))
    .list
    .apply()
    .map(a =>
      Character(a.name, a.`class`, a.flags, 0, Statstring(ByteString(a.statstring)))
    )
  }

  object DbUser extends SQLSyntaxSupport[DbUser] {
    override val tableName = "users"

    def apply(rs: WrappedResultSet) = new DbUser(
      rs.long(1),
      rs.longOpt(2),
      rs.string(3),
      rs.get[Array[Byte]](4),
      rs.get[Array[Byte]](5),
      rs.boolean(6),
      rs.string(7),
      rs.long(8),
      rs.long(9)
    )
  }

  private[db] def createUser(username: String, passwordHash: Array[Byte]) = {
    userCache.insert(username, passwordHash)
  }

  private[db] def updateUser(
    username: String,
    alias_id: Option[Long] = None,
    password_hash: Option[Array[Byte]] = None,
    flags: Option[Int] = None,
    closed: Option[Boolean] = None,
    closed_reason: Option[String] = None,
    last_logged_in: Option[Long] = None
  ) = {
    getUser(username).foreach(dbUser => {
      userCache.update(username, dbUser.copy(
        alias_id = if (alias_id.isDefined) alias_id else dbUser.alias_id,
        password_hash = password_hash.getOrElse(dbUser.password_hash),
        flags = flags.getOrElse(dbUser.flags),
        closed = closed.getOrElse(dbUser.closed),
        closed_reason = closed_reason.getOrElse(dbUser.closed_reason),
        last_logged_in = last_logged_in.getOrElse(dbUser.last_logged_in)
      ))
    })
  }

  def getUser(username: String) = userCache.get(username)
  def getUser(userId: Long) = userCache.get(userId)

  private[db] def saveInserted(inserted: Iterable[DbUser]) = {
    if (inserted.nonEmpty) {
      DB localTx { implicit session =>
        withSQL {
          InsertSQLBuilder(sqls"insert ignore into ${DbUser.table}")
            .namedValues(
              DbUser.column.username -> sqls.?,
              DbUser.column.alias_id -> sqls.?,
              DbUser.column.password_hash -> sqls.?,
              DbUser.column.flags -> sqls.?,
              DbUser.column.created -> sqls.?,
              DbUser.column.last_logged_in -> sqls.?
            )
        }.batch(inserted.map(
          dbUser => Seq(
            dbUser.username,
            dbUser.alias_id,
            dbUser.password_hash,
            dbUser.flags,
            dbUser.created,
            dbUser.last_logged_in
          )
        ).toSeq: _*)
          .apply()
      }
    }
  }

  private[db] def saveUpdated(updated: Iterable[DbUser]) = {
    if (updated.nonEmpty) {
      DB localTx { implicit session =>
        withSQL {
          update(DbUser)
            .set(
              DbUser.column.username -> sqls.?,
              DbUser.column.alias_id -> sqls.?,
              DbUser.column.password_hash -> sqls.?,
              DbUser.column.flags -> sqls.?,
              DbUser.column.closed -> sqls.?,
              DbUser.column.closed_reason -> sqls.?,
              DbUser.column.created -> sqls.?,
              DbUser.column.last_logged_in -> sqls.?
            )
              .where.eq(DbUser.column.column("id"), sqls.?) // applyDynamic does not support passing a vararg parameter?
        }.batch(
          updated.map(dbUser =>
            Seq(
              dbUser.username,
              dbUser.alias_id,
              dbUser.password_hash,
              dbUser.flags,
              dbUser.closed,
              dbUser.closed_reason,
              dbUser.created,
              dbUser.last_logged_in,
              dbUser.id
            )
          ).toSeq: _*)
            .apply()
      }
    }
  }

  object DbChannelJoin extends SQLSyntaxSupport[DbChannelJoin] {
    override val tableName = "channel_joins"

    def apply(rs: WrappedResultSet) = new DbChannelJoin(
      rs.long(1),
      rs.int(2),
      rs.long(3),
      rs.longOpt(4),
      rs.string(5),
      rs.long(6),
      rs.long(7),
      rs.long(8),
      rs.int(9),
      rs.boolean(10)
    )
  }

  private[db] def saveChannelJoin(channelJoin: DbChannelJoin) = {
    DB localTx { implicit session =>
      withSQL {
        insertInto(DbChannelJoin)
          .values(
            None,
            channelJoin.server_id,
            channelJoin.user_id,
            channelJoin.alias_id,
            channelJoin.channel,
            channelJoin.server_accepting_time,
            channelJoin.channel_created_time,
            channelJoin.joined_time,
            channelJoin.joined_place,
            channelJoin.is_operator
          )
      }
      .update()
      .apply()
    }
  }

  object DbFriendsList extends SQLSyntaxSupport[DbFriend] {
    override val tableName = "friends_list"

    def apply(rs: WrappedResultSet) = DbFriend(
      rs.long(1),
      rs.long(2),
      rs.int(3),
      rs.long(4),
      rs.string(5)
    )
  }

  private[db] def saveInsertedFriend(dbFriend: DbFriend) = {
    DB localTx { implicit session =>
      withSQL {
        insertInto(DbFriendsList)
          .values(
            None,
            dbFriend.user_id,
            dbFriend.friend_position,
            dbFriend.friend_id,
            dbFriend.friend_name
          )
      }
        .update()
        .apply()
    }
  }

  private[db] def saveUpdatedFriend(dbFriend: DbFriend) = {
    DB localTx { implicit session =>
      withSQL {
        update(DbFriendsList)
          .set(
            DbFriendsList.column.user_id -> dbFriend.user_id,
            DbFriendsList.column.friend_position -> dbFriend.friend_position,
            DbFriendsList.column.friend_id -> dbFriend.friend_id,
            DbFriendsList.column.friend_name -> dbFriend.friend_name
          ).where.eq(DbFriendsList.column.id, dbFriend.id)
      }
        .update()
        .apply()
    }
  }

  private[db] def saveDeletedFriend(dbFriend: DbFriend) = {
    DB localTx { implicit session =>
      withSQL {
        deleteFrom(DbFriendsList)
          .where.eq(DbFriendsList.column.column("id"), dbFriend.id) // applyDynamic does not support passing a vararg parameter?
      }
        .update()
        .apply()
    }
  }

  case class Ranking(server_id: Int, channel: String, user_id: Long, account_name: String, times_grabbed: Int)
  def getRankings: Seq[Ranking] = {
    DB localTx { implicit session =>
      sql"""
           SELECT tbl.* FROM (
               SELECT
                   server_id,
                   channel,
                   user_id,
                   accounts.username,
                   SUM(is_operator) as times_grabbed

               FROM channel_joins
               JOIN users accounts
               ON channel_joins.alias_id = accounts.id

               WHERE server_id = ${Config().Server.serverId}

               GROUP BY accounts.username, channel

               UNION ALL

               SELECT
                   server_id,
                   channel,
                   user_id,
                   accounts.username,
                   SUM(is_operator) as times_grabbed

               FROM channel_joins
               JOIN users accounts
               ON channel_joins.user_id = accounts.id

               WHERE server_id = ${Config().Server.serverId}
               AND channel_joins.alias_id IS NULL

               GROUP BY accounts.username, channel
           ) tbl WHERE times_grabbed > 0
           ORDER BY times_grabbed DESC
      """.map(rs => {
        Ranking(rs.int(1), rs.string(2), rs.long(3), rs.string(4), rs.int(5))
      }).list().apply()
    }
  }
}
