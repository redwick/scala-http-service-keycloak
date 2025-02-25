package server

import server.DBManager.PostgresSQL
import server.HttpManager.TextCodes
import org.apache.pekko.http.scaladsl.model.RemoteAddress
import org.slf4j.LoggerFactory
import slick.jdbc.PostgresProfile.api._
import slick.lifted.{TableQuery, Tag}

import java.util.{Calendar, Date}
import scala.collection.mutable.ListBuffer
import scala.concurrent.Future

trait DBLogger {

  case class AccessLog(id: Int, user: String, ip: String, cmd: String, date: Long)

  private val logger = LoggerFactory.getLogger(this.toString)

  object LogKinds {
    val Access = "access"
    val Error = "error"
    val Info = "info"
    val Warning = "warning"
  }


  case class CommandLog(id: Int, kind: String, user: String, ip: String, host: String, cmd: String, text: String, date: Long)
  case class CommandLogable(tag: Tag) extends Table[CommandLog](tag, "logs") {
    val id = column[Int]("id", O.AutoInc, O.PrimaryKey)
    val kind = column[String]("kind")
    val user = column[String]("user")
    val ip = column[String]("ip")
    val host = column[String]("host")
    val cmd = column[String]("cmd")
    val text = column[String]("text")
    val date = column[Long]("date")
    override def * = (id, kind, user, ip, host, cmd, text, date) <> ((CommandLog.apply _).tupled, CommandLog.unapply)
  }
  private val logTable = TableQuery[CommandLogable]

  PostgresSQL.run(DBIO.seq(
    logTable.schema.createIfNotExists,
  ))
  def saveInfoLog(text: String, hn: String, cmd: String = TextCodes.CodeError): Unit = {
    try{
      val d = new Date().getTime
      addLog(CommandLog(0, LogKinds.Info, TextCodes.Undefined, TextCodes.Undefined, hn, cmd, text, d))
    }
    catch {
      case e: Throwable => logger.error(e.toString)
    }
  }
  def saveErrorLog(text: String, u: String = TextCodes.Undefined, cmd: String = TextCodes.Undefined): Unit = {
    try{
      val d = new Date().getTime
      addLog(CommandLog(0, LogKinds.Error, u, TextCodes.Undefined, TextCodes.Undefined, cmd, text, d))
    }
    catch {
      case e: Throwable => logger.error(e.toString)
    }
  }
  def saveAccessLog(ip: RemoteAddress, text: String, cmd: String = TextCodes.Undefined, hn: String = TextCodes.Undefined, u: String = TextCodes.Undefined): Unit = {
    try{
      val ipAddr = ip.toOption.map(_.getHostAddress).getOrElse("unknown")
      val d = new Date().getTime
      addLog(CommandLog(0, LogKinds.Access, u, ipAddr, hn, cmd, text, d))
    }
    catch {
      case e: Throwable => logger.error(e.toString)
    }
  }
  def addLog(value: CommandLog): Future[Int] = {
    PostgresSQL.run(logTable.insertOrUpdate(value))
  }

  def getShortLogs: String = {
    val res = ListBuffer.empty[String]
    val logs = getLogs
    val d = Calendar.getInstance()
    d.add(Calendar.DAY_OF_MONTH, -20)
    (1.to(20)).foreach(x => {
      d.add(Calendar.DAY_OF_MONTH, 1)
      res += textLogsForDate(d.getTimeInMillis, logs)
    })

    val headers = List("date", "ip", "user", "requests", "time").mkString(";")
    headers + "\n" + res.filter(_.nonEmpty).reverse.mkString("\n\n")
  }
  private def textLogsForDate(date_long: Long, logs: List[AccessLog]): String = {
    val res = ListBuffer.empty[String]
    val date = Calendar.getInstance()
    date.setTime(new Date(date_long))
    date.set(Calendar.HOUR_OF_DAY, 0)
    date.set(Calendar.MINUTE, 0)
    val date_start = date.getTimeInMillis
    date.set(Calendar.HOUR_OF_DAY, 23)
    date.set(Calendar.MINUTE, 59)
    val date_end = date.getTimeInMillis


    val day = date.get(Calendar.DAY_OF_MONTH) + "-" + (date.get(Calendar.MONTH) + 1) + "-" + date.get(Calendar.YEAR)

    logs.filter(x => x.date >= date_start && x.date <= date_end).groupBy(x => (x.ip, x.user)).toList.sortBy(x => {
      val dates = x._2.sortBy(_.date).map(_.date)
      val sum = (dates.last - dates.head) / 1000 / 60
      val hour_breaks = ListBuffer.empty[Long]
      val breaks = ListBuffer.empty[Long]
      dates.foreach(x => {
        if (breaks.nonEmpty && x - breaks.last > 1000 * 60 * 60) {
          hour_breaks += x - breaks.last
        }
        breaks += x
      })
      sum - hour_breaks.sum
    }).reverse.foreach(ip => {
      val dates = ip._2.sortBy(_.date).map(_.date)
      val sum = (dates.last - dates.head) / 1000 / 60

      val hour_breaks = ListBuffer.empty[Long]
      val breaks = ListBuffer.empty[Long]
      dates.foreach(x => {
        if (breaks.nonEmpty && x - breaks.last > 1000 * 60 * 60) {
          hour_breaks += x - breaks.last
        }
        breaks += x
      })

      val total_sum = sum - hour_breaks.sum
      val time_spent = if (total_sum > 60){
        (total_sum / 60).toString + "h " + (total_sum % 60).toString + "m"
      }
      else{
        if (total_sum > 0) {
          total_sum.toString + "m"
        }
        else{
          "1m"
        }
      }

      res += List(day, ip._1._1, ip._1._2, ip._2.length, time_spent).mkString(";")
    })

    res.mkString("\n")
  }
  private def getLogs: List[AccessLog] = {
    val logs = ListBuffer.empty[AccessLog]
    val db = DBManager.GetPGConnection
    val s = db.createStatement()
    val rs = s.executeQuery("select * from logs")
    while (rs.next()) {
      logs += AccessLog(
        rs.getInt("id"),
        rs.getString("user"),
        rs.getString("ip"),
        rs.getString("cmd"),
        rs.getLong("date")
      )
    }
    rs.close()
    s.close()
    db.close()
    logs.toList
  }
}
