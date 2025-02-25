package server.users.tables

import io.circe.generic.JsonCodec
import org.apache.pekko.actor.typed.ActorRef
import org.slf4j.LoggerFactory
import server.DBManager
import server.app.Envs.{MessagesRu, TextCodes}
import server.http.AppMessage.MailManagerMessage
import server.http.RealmUser
import server.mail.MailManager.Mail
import slick.collection.heterogeneous.HNil
import slick.jdbc.PostgresProfile.api._
import slick.lifted.{TableQuery, Tag}

import java.math.BigInteger
import java.security.MessageDigest
import java.util.{Calendar, UUID}
import scala.collection.mutable.ListBuffer
import scala.util.Random

trait UsersTable {
  private val logger = LoggerFactory.getLogger(this.toString)


  @JsonCodec case class EmailCode(hash: String, code: Int, verified: Int, date: Long)
  @JsonCodec case class GetEmailCode(username: String, email: String)

  @JsonCodec case class UserContractPost(
                                          uid: String,
                                          date: String,
                                          number: String,
                                          name: String,
                                          file_url: String
                                        )

  case class EmailCodeTable(tag: Tag) extends Table[EmailCode](tag, "user_email_code") {
    val hash = column[String]("hash")
    val code = column[Int]("code")
    val verified = column[Int]("verified")
    val date = column[Long]("date")

    def * = (
      hash :: code :: verified :: date :: HNil
      ).mapTo[EmailCode]
  }

  val userEmailCodeTable = TableQuery[EmailCodeTable]

  def sendVerifyEmail(email: String, mail: ActorRef[MailManagerMessage]): Unit = {
    val hashed = hash(email)
    val code = Random.between(100000, 999999)
    addEmailCode(hashed, code)
    sendEmailCode(email, code, mail: ActorRef[MailManagerMessage])
  }
  private def addEmailCode(hash: String, code: Int): Unit = {
    try {
      val connection = DBManager.GetPGConnection
      val d = Calendar.getInstance().getTime.getTime
      val q = s"insert into user_email_code (hash, code, verified, date) values ('$hash', $code, 0, $d)"
      val stmt = connection.createStatement()
      stmt.execute(q)
      stmt.close()
      connection.close()
    }
    catch {
      case e: Throwable =>
        logger.error(e.toString)
    }
  }
  def checkVerifyEmailCode(email: String, code: Int): Boolean = {
    try {
      val hashed = hash(email)
      val d = Calendar.getInstance().getTime.getTime
      val h24 = 24 * 60 * 60 * 1000
      val connection = DBManager.GetPGConnection
      val stmt = connection.createStatement()
      val rs = stmt.executeQuery(s"select * from user_email_code where hash = '$hashed' and code = $code and ($d - date) < $h24")
      val exists = rs.next()
      if (exists) {
        stmt.execute(s"update user_email_code set verified = 1 where hash = '$hashed' and code = $code and ($d - date) < $h24")
      }
      rs.close()
      stmt.close()
      connection.close()
      exists
    }
    catch {
      case e: Throwable =>
        logger.error(e.toString)
        false
    }
  }
  def checkEmailValid(email: String): Boolean = {
    try {
      val hashed = hash(email)
      val d = Calendar.getInstance().getTime.getTime
      val h24 = 24 * 60 * 60 * 1000
      val connection = DBManager.GetPGConnection
      val stmt = connection.createStatement()
      val rs = stmt.executeQuery(s"select * from user_email_code where hash = '$hashed' and verified = 1 and ($d - date) < $h24")
      val exists = rs.next()
      rs.close()
      stmt.close()
      connection.close()
      exists
    }
    catch {
      case e: Throwable =>
        logger.error(e.toString)
        false
    }
  }
  def createUserName(users: List[RealmUser]): String = {
    val names = ListBuffer.empty[String]
    while (names.isEmpty || users.exists(_.username == names.last)) {
      names += "user." + UUID.randomUUID().toString.replace("-", "").take(8)
    }
    names.last
  }
  def checkPasswordValid(password: String): Boolean = {
    "(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,}$".r.findFirstIn(password).getOrElse("") == password
  }
  def checkEmailAddressValid(email: String): Boolean = {
    ".+@.+\\..+".r.findFirstIn(email).getOrElse("") == email
  }
  private def sendEmailCode(email: String, code: Int, mail: ActorRef[MailManagerMessage]): Unit = {
    mail.tell(Mail(email, TextCodes.VerifyEmailCodeSubject, MessagesRu.YourVerificationCode + " " + code + "\n" + MessagesRu.ValidIn24h))
  }
  def sendGreetings(email: String, mail: ActorRef[MailManagerMessage]): Unit = {
    mail.tell(Mail(email, TextCodes.GreetingEmailSubject, MessagesRu.GreetingEmail))
  }
  private def hash(value: String): String = {
    String.format("%064x", new BigInteger(1, MessageDigest.getInstance("SHA-256").digest(value.getBytes("UTF-8"))))
  }

}
