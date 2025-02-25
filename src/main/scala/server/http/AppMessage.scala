package server.http

import org.apache.pekko.actor.typed.ActorRef



object AppMessage {

  trait AppRequestMessage
  trait AppResponseMessage

  case class AppSender(ref: ActorRef[AppResponseMessage], user: AuthUser, users: List[RealmUser], mail: ActorRef[MailManagerMessage], ws: ActorRef[WebSocketManagerMessage], date: Long) extends AppRequestMessage

  case class SuccessTextResponse(text: String) extends AppResponseMessage
  case class ErrorTextResponse(text: String) extends AppResponseMessage
  case class NotAllowedTextResponse(text: String) extends AppResponseMessage
  case class BadRequestTextResponse(text: String) extends AppResponseMessage

  trait SomethingManagerMessage extends AppRequestMessage
  trait FileManagerMessage extends AppRequestMessage
  trait CloudManagerMessage extends AppRequestMessage
  trait UsersManagerMessage extends AppRequestMessage
  trait MailManagerMessage extends AppRequestMessage
  trait ContractManagerMessage extends AppRequestMessage
  trait WebSocketManagerMessage extends AppRequestMessage

}
