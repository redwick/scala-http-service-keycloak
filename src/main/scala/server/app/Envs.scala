package server.app

import org.slf4j.LoggerFactory

import scala.util.Properties

object Envs extends Codes {

  private val logger = LoggerFactory.getLogger("props")

  val sso_clientId: String = Properties.envOrElse("sso_clientId", {
    logger.error(EnvCodes.SSOClientIdNotSet)
    ""
  })
  val sso_secretKey: String = Properties.envOrElse("sso_secretKey", {
    logger.error(EnvCodes.SSOSecretKeyNotSet)
    ""
  })
  val pg_host: String = Properties.envOrElse("pg_host", {
    logger.error(EnvCodes.PGHostNotSet)
    ""
  })
  val pg_pass: String = Properties.envOrElse("pg_pass", {
    logger.error(EnvCodes.PGPassNotSet)
    ""
  })
  val mail_login: String = Properties.envOrElse("mail_login", {
    logger.error(EnvCodes.MailLoginNotSet)
    ""
  })
  val mail_password: String = Properties.envOrElse("mail_password", {
    logger.error(EnvCodes.MailPasswordNotSet)
    ""
  })


  def check(): Boolean ={
    !List(pg_host, pg_pass, mail_login, mail_password).contains("")
  }

}
