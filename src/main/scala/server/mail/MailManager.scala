package server.mail

import com.typesafe.config.{Config, ConfigFactory}
import server.app.Envs
import server.http.AppMessage.{AppSender, MailManagerMessage}
import org.apache.pekko.actor.typed.Behavior
import org.apache.pekko.actor.typed.scaladsl.Behaviors
import org.simplejavamail.api.mailer.config.TransportStrategy
import org.simplejavamail.email.EmailBuilder
import org.simplejavamail.mailer.MailerBuilder
import org.slf4j.LoggerFactory

object MailManager {

  private val config: Config = ConfigFactory.load()
  private val logger = LoggerFactory.getLogger(this.toString)


  case class Mail(to: String, subject: String, text: String) extends MailManagerMessage
  case class MailHtml(mail: Mail, html: String, params: Map[String, String]) extends MailManagerMessage

  def apply(): Behavior[MailManagerMessage] = Behaviors.setup(context => {
    Behaviors.receiveMessage({
      case (mail: Mail) =>
        sendEmail(mail)
        Behaviors.same
      case _ =>
        Behaviors.same
    })
  })
  private def sendEmail(mail: Mail): Unit = {
    try{
      val email = EmailBuilder.startingBlank()
        .to(mail.to)
        .from(config.getString("mail.fromName"), config.getString("mail.fromAddress"))
        .withSubject(mail.subject)
        .withPlainText(mail.text)
        .buildEmail()
      val mailer = MailerBuilder
        .withSMTPServer(config.getString("mail.host"), config.getInt("mail.port"), Envs.mail_login, Envs.mail_password)
        .withTransportStrategy(TransportStrategy.SMTPS)
        .async()
        .buildMailer()
      mailer.sendMail(email)
    }
    catch {
      case e: Throwable => logger.error(e.toString)
    }
  }
}
