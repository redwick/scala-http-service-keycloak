package server

import com.typesafe.config.ConfigFactory
import io.circe.jawn
import io.circe.parser.parse
import io.circe.syntax.EncoderOps
import server.app.{Codes, Envs}
import server.http.AppMessage._
import server.http.{AuthUser, RealmGroup, RealmUser}
import server.users.UsersManager._
import org.apache.pekko.actor.typed.scaladsl.AskPattern.{Askable, schedulerFromActorSystem}
import org.apache.pekko.actor.typed.{ActorRef, ActorSystem}
import org.apache.pekko.http.cors.scaladsl.CorsDirectives._
import org.apache.pekko.http.scaladsl.Http
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.model.headers.{HttpCredentials, OAuth2BearerToken}
import org.apache.pekko.http.scaladsl.server.Directives._
import org.apache.pekko.http.scaladsl.server.Route
import org.apache.pekko.util.Timeout
import org.slf4j.LoggerFactory
import java.util.{Calendar, TimeZone}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.{Duration, MINUTES, SECONDS}

object HttpManager extends Codes with DBLogger {

  private val logger = LoggerFactory.getLogger("server/http")
  private val config = ConfigFactory.load()


  def apply(system: ActorSystem[Nothing],
            mail: ActorRef[MailManagerMessage],
            users: ActorRef[(UsersManagerMessage, AppSender)],
           ): Future[Http.ServerBinding] = {
    try {
      TimeZone.setDefault(TimeZone.getTimeZone("Europe/Moscow"))
      implicit val sys: ActorSystem[Nothing] = system
      implicit val timeout: Timeout = Duration(5, SECONDS)
      val route: Route = cors() {
        concat(
          basicRoutes(),
          userRegisterRoutes(system, mail),
        )
      }
      logger.info("http started at " + config.getString("http.host") + ":" + config.getString("http.port"))
      Http().newServerAt(config.getString("http.host"), config.getInt("http.port")).bind(route)
    }
    catch {
      case e: Throwable =>
        println(e.toString)
        Thread.sleep(5 * 1000)
        HttpManager(system, mail, users)
    }
  }


  def forward[A <: AppRequestMessage](system: ActorSystem[_], actorRef: ActorRef[(A, AppSender)], message: A, mail: ActorRef[MailManagerMessage], ws: ActorRef[WebSocketManagerMessage]): Route = {
    try {
      implicit val sys: ActorSystem[Nothing] = system
      implicit val timeout: Timeout = Duration(1, MINUTES)
      val date = Calendar.getInstance().getTime.getTime
      (extractHost & extractClientIP & extractCredentials){ (hn, ip, credentials) =>
        val token = parseCredentials(credentials)
        if (token != "") {
          val future = for {
            user <- getUser(system, token)
            service_token <- getToken(system)
            users <- getUsers(system, service_token)
          } yield {
            if (user.active || token == "debug"){
              saveAccessLog(ip, TextCodes.Forwarded, message.toString, hn, user.userName)
              parseResponse(actorRef.ask((replyTo: ActorRef[AppResponseMessage]) => {
                (message, AppSender(replyTo, user, users, mail, ws, date))
              }))
            }
            else{
              saveAccessLog(ip, TextCodes.TokenNotValid, message.toString, hn)
              Future.successful(HttpResponse(StatusCodes.MethodNotAllowed, entity = HttpEntity(TextCodesRu.TokenExpiredOrInvalid)))
            }
          }
          complete(future)
        }
        else{
          saveAccessLog(ip, TextCodes.TokenNotFound, message.toString, hn)
          complete(HttpResponse(StatusCodes.Unauthorized, entity = HttpEntity(TextCodesRu.NoToken)))
        }
      }
    }
    catch {
      case e: Throwable =>
        saveErrorLog(e.toString, cmd = message.toString)
        complete(HttpResponse(StatusCodes.InternalServerError, entity = HttpEntity(e.toString.asJson.noSpaces)))
    }
  }

  private def basicRoutes(): Route = {
    concat(
      (get & path("time")) {
        complete(HttpResponse(StatusCodes.OK, entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, Calendar.getInstance().getTime.toString.asJson.noSpaces)))
      },
      (get & path("time-text")) {
        complete(HttpResponse(StatusCodes.OK, entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, Calendar.getInstance().getTime.toString)))
      },
      (get & path("time-json")) {
        complete(HttpResponse(StatusCodes.OK, entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, Calendar.getInstance().getTime.toString.asJson.noSpaces)))
      },
      (get & path("logs-short")) {
        complete(HttpResponse(StatusCodes.OK, entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, getShortLogs)))
      },
      (get & path("logs-short-csv")) {
        complete(HttpResponse(StatusCodes.OK, entity = HttpEntity(ContentTypes.`text/csv(UTF-8)`, getShortLogs)))
      },
    )
  }
  private def userRegisterRoutes(system: ActorSystem[_], mail: ActorRef[MailManagerMessage]): Route = {
    concat(
      (post & path("regUser") & entity(as[String]) & extractCredentials & extractClientIP & extractHost) { (json, credentials, ip, hn) =>
        val future = for {
          service_token <- getToken(system)
          users <- getUsers(system, service_token)
        } yield {
          saveAccessLog(ip, TextCodes.Forwarded, "Reg User: " + json, hn, "anonymous")
          parseUser(json, createUserName(users)) match {
            case Right(user) =>
              if (checkEmailValid(user.email.trim.toLowerCase)){
                if (checkPasswordValid(user.credentials.head.value)){
                  regUser(system, service_token, user).flatMap({
                    case Left(exception) =>
                      Future.successful(HttpResponse(StatusCodes.InternalServerError, entity = HttpEntity(exception.asJson.noSpaces)))
                    case Right(_) =>
                      sendGreetings(user.email, mail)
                      Future.successful(HttpResponse(StatusCodes.OK, entity = HttpEntity(TextCodesRu.UserRegistered)))
                  })
                }
                else{
                  Future.successful(HttpResponse(StatusCodes.BadRequest, entity = HttpEntity(TextCodesRu.PasswordNotValid)))
                }
              }
              else{
                Future.successful(HttpResponse(StatusCodes.BadRequest, entity = HttpEntity(TextCodesRu.EmailNotVerified)))
              }
            case Left(exception) =>
              Future.successful(HttpResponse(StatusCodes.InternalServerError, entity = HttpEntity(exception.asJson.noSpaces)))
          }
        }
        complete(future)
      },
      (post & path("sendVerifyEmail") & entity(as[String]) & extractCredentials & extractClientIP & extractHost) { (json, credentials, ip, hn) =>
        val future = for {
          service_token <- getToken(system)
          users <- getUsers(system, service_token)
        } yield {
          saveAccessLog(ip, TextCodes.Forwarded, "Try Reg User: " + json, hn, "anonymous")
          parseTryUser(json) match {
            case Right(tryRegUser) =>
              if (!users.exists(_.email.trim.toLowerCase == tryRegUser.email.trim.toLowerCase)){
                if (checkEmailAddressValid(tryRegUser.email.trim.toLowerCase)){
                  sendVerifyEmail(tryRegUser.email.trim.toLowerCase, mail)
                  Future.successful(HttpResponse(StatusCodes.OK, entity = HttpEntity(TextCodesRu.VerifyCodeHasBeenSend)))
                }
                else{
                  Future.successful(HttpResponse(StatusCodes.BadRequest, entity = HttpEntity(TextCodesRu.EmailIsNotValid)))
                }
              }
              else{
                Future.successful(HttpResponse(StatusCodes.Conflict, entity = HttpEntity(TextCodesRu.EmailAlreadyRegistered)))
              }
            case Left(exception) =>
              Future.successful(HttpResponse(StatusCodes.InternalServerError, entity = HttpEntity(exception.asJson.noSpaces)))
          }
        }
        complete(future)
      },
      (post & path("checkVerifyEmail") & entity(as[String]) & extractCredentials & extractClientIP & extractHost) { (json, credentials, ip, hn) =>
        saveAccessLog(ip, TextCodes.Forwarded, "Check Reg User: " + json, hn, "anonymous")
        complete(parseCheckUser(json) match {
          case Right(tryRegUser) =>
            if (checkVerifyEmailCode(tryRegUser.email.trim.toLowerCase, tryRegUser.code)){
              Future.successful(HttpResponse(StatusCodes.OK, entity = HttpEntity(TextCodes.OK)))
            }
            else{
              Future.successful(HttpResponse(StatusCodes.BadRequest, entity = HttpEntity(TextCodesRu.VerifyCodeNotValid)))
            }
          case Left(exception) =>
            Future.successful(HttpResponse(StatusCodes.InternalServerError, entity = HttpEntity(exception.asJson.noSpaces)))
        })
      },
      (get & path("users") & parameter("group") & extractCredentials & extractClientIP & extractHost) { (group, credentials, ip, hn) =>
        val token = parseCredentials(credentials)
        if (token != "") {
          val future = for {
            auth <- getUser(system, token)
            service_token <- getToken(system)
            users <- getUsers(system, service_token)
          } yield {
            if (auth.active || token == "debug"){
              saveAccessLog(ip, TextCodes.Forwarded, "Get Groups", hn, auth.userName)
              getGroups(system, service_token).flatMap(groups => {
                groups.find(_.name == group) match {
                  case Some(group) =>
                    getGroupMembers(system, service_token, group.id).flatMap(members => {
                      Future.successful(HttpResponse(StatusCodes.OK, entity = HttpEntity(members.asJson.noSpaces)))
                    })
                  case None =>
                    Future.successful(HttpResponse(StatusCodes.OK, entity = HttpEntity(List.empty[RealmUser].asJson.noSpaces)))
                }
              })
            }
            else{
              saveAccessLog(ip, TextCodes.TokenNotValid, "Get Groups", hn)
              Future.successful(HttpResponse(StatusCodes.MethodNotAllowed, entity = HttpEntity(TextCodesRu.TokenExpiredOrInvalid)))
            }
          }
          complete(future)
        }
        else{
          saveAccessLog(ip, TextCodes.TokenNotFound, "Get Groups", hn)
          complete(HttpResponse(StatusCodes.Unauthorized, entity = HttpEntity(TextCodesRu.NoToken)))
        }
      },
      (get & path("users") & extractCredentials & extractClientIP & extractHost) { (credentials, ip, hn) =>
        val token = parseCredentials(credentials)
        if (token != "") {
          val future = for {
            auth <- getUser(system, token)
            service_token <- getToken(system)
            users <- getUsers(system, service_token)
          } yield {
            if (auth.active || token == "debug"){
              saveAccessLog(ip, TextCodes.Forwarded, "Get Users", hn, auth.userName)
              Future.successful(HttpResponse(StatusCodes.OK, entity = HttpEntity(users.asJson.noSpaces)))
            }
            else{
              saveAccessLog(ip, TextCodes.TokenNotValid, "Get Users", hn)
              Future.successful(HttpResponse(StatusCodes.MethodNotAllowed, entity = HttpEntity(TextCodesRu.TokenExpiredOrInvalid)))
            }
          }
          complete(future)
        }
        else{
          saveAccessLog(ip, TextCodes.TokenNotFound, "Get Users", hn)
          complete(HttpResponse(StatusCodes.Unauthorized, entity = HttpEntity(TextCodesRu.NoToken)))
        }
      },
      (get & path("groups") & extractCredentials & extractClientIP & extractHost) { (credentials, ip, hn) =>
        val token = parseCredentials(credentials)
        if (token != "") {
          val future = for {
            auth <- getUser(system, token)
            service_token <- getToken(system)
            users <- getUsers(system, service_token)
          } yield {
            if (auth.active || token == "debug"){
              saveAccessLog(ip, TextCodes.Forwarded, "Get Groups", hn, auth.userName)
              getGroups(system, service_token).flatMap(groups => {
                Future.successful(HttpResponse(StatusCodes.OK, entity = HttpEntity(groups.asJson.noSpaces)))
              })
            }
            else{
              saveAccessLog(ip, TextCodes.TokenNotValid, "Get Groups", hn)
              Future.successful(HttpResponse(StatusCodes.MethodNotAllowed, entity = HttpEntity(TextCodesRu.TokenExpiredOrInvalid)))
            }
          }
          complete(future)
        }
        else{
          saveAccessLog(ip, TextCodes.TokenNotFound, "Get Groups", hn)
          complete(HttpResponse(StatusCodes.Unauthorized, entity = HttpEntity(TextCodesRu.NoToken)))
        }
      },
      (get & path("userGroup") & parameter("user_id", "from", "to") & extractCredentials & extractClientIP & extractHost) { (user_id, from, to, credentials, ip, hn) =>
        val token = parseCredentials(credentials)
        if (token != "") {
          val future = for {
            auth <- getUser(system, token)
            service_token <- getToken(system)
            users <- getUsers(system, service_token)
          } yield {
            if (auth.active || token == "debug"){
              saveAccessLog(ip, TextCodes.Forwarded, "User Move To Group", hn, auth.userName)
              getGroups(system, service_token).flatMap(groups => {
                val fromGroup = groups.find(_.name == from)
                val toGroup = groups.find(_.name == to)
                if (fromGroup.nonEmpty && toGroup.nonEmpty){
                  userMoveToGroup(system, service_token, user_id, fromGroup.get.id, toGroup.get.id).flatMap(result => {
                    Future.successful(HttpResponse(StatusCodes.OK, entity = HttpEntity(TextCodes.OK)))
                  })
                }
                else if (fromGroup.nonEmpty){
                  userRemoveFromGroup(system, service_token, user_id, fromGroup.get.id).flatMap(result => {
                    Future.successful(HttpResponse(StatusCodes.OK, entity = HttpEntity(TextCodes.OK)))
                  })
                }
                else if (toGroup.nonEmpty){
                  userAddToGroup(system, service_token, user_id, toGroup.get.id).flatMap(result => {
                    Future.successful(HttpResponse(StatusCodes.OK, entity = HttpEntity(TextCodes.OK)))
                  })
                }
                else{
                  Future.successful(HttpResponse(StatusCodes.BadRequest, entity = HttpEntity(TextCodes.NotFound)))
                }
              })
            }
            else{
              saveAccessLog(ip, TextCodes.TokenNotValid, "User Move To Group", hn)
              Future.successful(HttpResponse(StatusCodes.MethodNotAllowed, entity = HttpEntity(TextCodesRu.TokenExpiredOrInvalid)))
            }
          }
          complete(future)
        }
        else{
          saveAccessLog(ip, TextCodes.TokenNotFound, "User Move To Group", hn)
          complete(HttpResponse(StatusCodes.Unauthorized, entity = HttpEntity(TextCodesRu.NoToken)))
        }
      },
    )
  }


  private def parseResponse(ask: Future[AppResponseMessage]): Future[HttpResponse] = {
    ask.flatMap {
      case SuccessTextResponse(text) => Future.successful(
        HttpResponse(StatusCodes.OK, entity = HttpEntity(text))
      )
      case NotAllowedTextResponse(text) => Future.successful(
        HttpResponse(StatusCodes.MethodNotAllowed, entity = HttpEntity(text))
      )
      case BadRequestTextResponse(text) => Future.successful(
        HttpResponse(StatusCodes.BadRequest, entity = HttpEntity(text))
      )
      case ErrorTextResponse(text) => Future.successful(
        HttpResponse(StatusCodes.InternalServerError, entity = HttpEntity(text))
      )
      case _ => Future.successful(
        HttpResponse(StatusCodes.InternalServerError, entity = HttpEntity(TextCodesRu.ServerError))
      )
    }
  }
  private def parseCredentials(credentials: Option[HttpCredentials]): String = {
    credentials match {
      case Some(credentialsValue) => credentialsValue match {
        case tokenValue: OAuth2BearerToken => tokenValue.token
        case _ => ""
      }
      case _ => ""
    }
  }
  private def getUsers(system: ActorSystem[_], token: String): Future[List[RealmUser]] = {
    implicit val sys: ActorSystem[Nothing] = system
    val url = config.getString("sso.usersUrl")
    val request = HttpRequest(HttpMethods.GET, uri = url).addCredentials(OAuth2BearerToken(token))
    Http(sys).singleRequest(request).flatMap(requestValue => {
      requestValue.entity.toStrict(Duration(1, MINUTES)).map(_.data.utf8String).flatMap(jsonValue => {
        parse(jsonValue) match {
          case Right(json) =>
            val users = json.as[List[RealmUser]].getOrElse(List.empty[RealmUser])
            Future.successful(users)
          case Left(exception) =>
            Future.successful(List.empty[RealmUser])
        }
      })
    })
  }
  private def getGroups(system: ActorSystem[_], token: String): Future[List[RealmGroup]] = {
    implicit val sys: ActorSystem[Nothing] = system
    val url = config.getString("sso.groupsUrl")
    val request = HttpRequest(HttpMethods.GET, uri = url).addCredentials(OAuth2BearerToken(token))
    Http(sys).singleRequest(request).flatMap(requestValue => {
      requestValue.entity.toStrict(Duration(1, MINUTES)).map(_.data.utf8String).flatMap(jsonValue => {
        parse(jsonValue) match {
          case Right(json) =>
            val users = json.as[List[RealmGroup]].getOrElse(List.empty[RealmGroup])
            Future.successful(users)
          case Left(exception) =>
            Future.successful(List.empty[RealmGroup])
        }
      })
    })
  }
  private def userMoveToGroup(system: ActorSystem[_], token: String, user_id: String, from_group_id: String, to_group_id: String): Future[String] = {
    userAddToGroup(system, token, user_id, to_group_id).flatMap(_ => {
      userRemoveFromGroup(system, token, user_id, from_group_id).flatMap(_ => {
        Future.successful(TextCodes.OK)
      })
    })
  }
  private def userAddToGroup(system: ActorSystem[_], token: String, user_id: String, group_id: String): Future[String] = {
    implicit val sys: ActorSystem[Nothing] = system
    val url = config.getString("sso.usersUrl") + "/" + user_id + "/groups/" + group_id
    val request = HttpRequest(HttpMethods.PUT, uri = url).addCredentials(OAuth2BearerToken(token))
    Http(sys).singleRequest(request).flatMap(requestValue => {
      requestValue.entity.toStrict(Duration(1, MINUTES)).map(_.data.utf8String).flatMap(_ => {
        Future.successful(TextCodes.OK)
      })
    })
  }
  private def userRemoveFromGroup(system: ActorSystem[_], token: String, user_id: String, group_id: String): Future[String] = {
    implicit val sys: ActorSystem[Nothing] = system
    val url = config.getString("sso.usersUrl") + "/" + user_id + "/groups/" + group_id
    val request = HttpRequest(HttpMethods.DELETE, uri = url).addCredentials(OAuth2BearerToken(token))
    Http(sys).singleRequest(request).flatMap(requestValue => {
      requestValue.entity.toStrict(Duration(1, MINUTES)).map(_.data.utf8String).flatMap(_ => {
        Future.successful(TextCodes.OK)
      })
    })
  }
  private def getGroupMembers(system: ActorSystem[_], token: String, group_id: String): Future[List[RealmUser]] = {
    implicit val sys: ActorSystem[Nothing] = system
    val url = config.getString("sso.groupsUrl") + "/" + group_id + "/members"
    val request = HttpRequest(HttpMethods.GET, uri = url).addCredentials(OAuth2BearerToken(token))
    Http(sys).singleRequest(request).flatMap(requestValue => {
      requestValue.entity.toStrict(Duration(1, MINUTES)).map(_.data.utf8String).flatMap(jsonValue => {
        parse(jsonValue) match {
          case Right(json) =>
            val users = json.as[List[RealmUser]].getOrElse(List.empty[RealmUser])
            Future.successful(users)
          case Left(exception) =>
            Future.successful(List.empty[RealmUser])
        }
      })
    })
  }

  private def regUser(system: ActorSystem[_], token: String, user: RegisterUser): Future[Either[String, Unit]] = {
    implicit val sys: ActorSystem[Nothing] = system
    val url = config.getString("sso.usersUrl")
    val request = HttpRequest(HttpMethods.POST, entity = HttpEntity(ContentTypes.`application/json`, user.asJson.noSpaces), uri = url).addCredentials(OAuth2BearerToken(token))
    Http(sys).singleRequest(request).flatMap(requestValue => {
      requestValue.entity.toStrict(Duration(1, MINUTES)).map(_.data.utf8String).flatMap(data => {
        Future.successful(jawn.decode[ErrorMessage](data) match {
          case Left(exception) => Right()
          case Right(json) => Left(json.errorMessage)
        })
      })
    })
  }
  private def sendUserVerifyEmail(system: ActorSystem[_], token: String, user_id: String): Future[Either[String, Unit]] = {
    implicit val sys: ActorSystem[Nothing] = system
    val url = config.getString("sso.userSendVerifyEmail").replace("&user_id", user_id)
    val request = HttpRequest(HttpMethods.PUT, uri = url).addCredentials(OAuth2BearerToken(token))
    Http(sys).singleRequest(request).flatMap(requestValue => {
      requestValue.entity.toStrict(Duration(1, MINUTES)).map(_.data.utf8String).flatMap(data => {
        Future.successful(jawn.decode[ErrorMessage](data) match {
          case Left(exception) => Right()
          case Right(json) => Left(json.errorMessage)
        })
      })
    })
  }
  private def getUser(system: ActorSystem[_], token: String): Future[AuthUser] = {
    implicit val sys: ActorSystem[Nothing] = system
    val url = config.getString("sso.introspectUrl")
    val client_id = Envs.sso_clientId
    val client_secret = Envs.sso_secretKey
    val request = HttpRequest(HttpMethods.POST, uri = url, entity = FormData("client_id" -> client_id, "client_secret" -> client_secret, "token" -> token).toEntity)
    Http(sys).singleRequest(request).flatMap(requestValue => {
      requestValue.entity.toStrict(Duration(1, MINUTES)).map(_.data.utf8String).flatMap(jsonValue => {
        parse(jsonValue) match {
          case Right(json) =>
            val active = json.hcursor.downField("active").as[Boolean].getOrElse(false)
            val user = if (active) {
              val name = json.hcursor.downField("name").as[String].getOrElse("")
              val email = json.hcursor.downField("email").as[String].getOrElse("")
              val username = json.hcursor.downField("username").as[String].getOrElse("")
              val id = json.hcursor.downField("sub").as[String].getOrElse("")
              val roles = json.hcursor.downField("realm_access").downField("roles").as[List[String]].getOrElse(List.empty[String])
              AuthUser(active, id, username, name, email, roles)
            }
            else{
              AuthUser(active)
            }
            Future.successful(user)
          case Left(exception) =>
            Future.successful(AuthUser())
        }
      })
    })
  }
  private def getToken(system: ActorSystem[_]): Future[String] = {
    implicit val sys: ActorSystem[Nothing] = system
    val url = config.getString("sso.authUrl")
    val client_id = Envs.sso_clientId
    val client_secret = Envs.sso_secretKey
    val requestToken = HttpRequest(HttpMethods.POST, uri = url, entity = FormData("client_id" -> client_id, "client_secret" -> client_secret, "grant_type" -> "client_credentials").toEntity)
    Http(sys).singleRequest(requestToken).flatMap(requestValue => {
      requestValue.entity.toStrict(Duration(1, MINUTES)).map(_.data.utf8String).flatMap(jsonValue => {
        parse(jsonValue) match {
          case Right(json) =>
            val access_token = json.hcursor.downField("access_token").as[String].getOrElse("")
            Future.successful(access_token)
          case Left(exception) =>
            Future.successful("")
        }
      })
    })
  }
}
