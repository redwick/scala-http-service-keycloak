package server.users.controls

import io.circe.generic.JsonCodec
import io.circe.parser.decode
import org.slf4j.LoggerFactory
import server.users.tables.UsersTable

trait UsersControl extends UsersTable {

  private val logger = LoggerFactory.getLogger(this.toString)


  @JsonCodec case class ErrorMessage(errorMessage: String)
  @JsonCodec case class RegisterUser(username: String, email: String, enabled: Boolean = true, emailVerified: Boolean = true,
                                     firstName: String, lastName: String, groups: List[String] = List("guest"),
                                     requiredActions: List[String] = List(), attributes: RegisterUserAttributes, credentials: List[RegisterUserCredentials])

  @JsonCodec case class RegisterUserAttributes(phone: List[String], department: List[String], jobTitle: List[String])
  @JsonCodec case class RegisterUserCredentials(`type`: String = "password", value: String)

  @JsonCodec case class RegisterUserPostJson(email: String, firstName: String, lastName: String, phone: String, department: String, jobTitle: String, password: String)
  @JsonCodec case class TryRegisterUserPostJson(email: String)
  @JsonCodec case class CheckRegisterUserPostJson(email: String, code: Int)


  def parseUser(json: String, userName: String): Either[String, RegisterUser] = {
    decode[RegisterUserPostJson](json) match {
      case Right(value) =>
        Right(RegisterUser(
          username = userName,
          email = value.email,
          firstName = value.firstName,
          lastName = value.lastName,
          attributes = RegisterUserAttributes(List(value.phone), List(value.department), List(value.jobTitle)),
          credentials = List(RegisterUserCredentials(value = value.password))
        ))
      case Left(error) =>
        logger.error(error.getMessage)
        Left(error.getMessage)
    }
  }
  def parseTryUser(json: String): Either[String, TryRegisterUserPostJson] = {
    decode[TryRegisterUserPostJson](json) match {
      case Right(value) => Right(value)
      case Left(error) =>
        logger.error(error.getMessage)
        Left(error.getMessage)
    }
  }
  def parseCheckUser(json: String): Either[String, CheckRegisterUserPostJson] = {
    decode[CheckRegisterUserPostJson](json) match {
      case Right(value) => Right(value)
      case Left(error) =>
        logger.error(error.getMessage)
        Left(error.getMessage)
    }
  }

}
