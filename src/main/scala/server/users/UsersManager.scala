package server.users

import server.http.AppMessage.{AppSender, UsersManagerMessage}
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import server.DBManager.PostgresSQL
import server.users.controls.UsersControl
import server.users.tables.UsersTable
import slick.dbio.DBIO
import slick.jdbc.PostgresProfile.api._

object UsersManager extends UsersTable with UsersControl{
  def apply(): Behavior[(UsersManagerMessage, AppSender)] = Behaviors.setup(context => {

    PostgresSQL.run(DBIO.seq(
      userEmailCodeTable.schema.createIfNotExists,
    ))

    Behaviors.same
  })
}
