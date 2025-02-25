package server.http

import io.circe.{Decoder, Encoder, HCursor, Json}

object RealmUser{
  implicit val decodeUser: Decoder[RealmUser] = new Decoder[RealmUser] {
    final def apply(c: HCursor): Decoder.Result[RealmUser] =
      for {
        id <- c.downField("id").as[String]
        username <- c.downField("username").as[String]
        firstName <- c.downField("firstName").as[String]
        lastName <- c.downField("lastName").as[String]
        email <- c.downField("email").as[String]
      } yield {
        new RealmUser(id, username, firstName, lastName, email)
      }
  }
  implicit val encodeUser: Encoder[RealmUser] = new Encoder[RealmUser] {
    final def apply(a: RealmUser): Json = Json.obj(
      ("id", Json.fromString(a.id)),
      ("username", Json.fromString(a.username)),
      ("firstName", Json.fromString(a.firstName)),
      ("lastName", Json.fromString(a.lastName)),
      ("email", Json.fromString(a.email)),
    )
  }
  def empty = new RealmUser("", "", "", "", "")
}
class RealmUser(val id: String, val username: String, val firstName: String, val lastName: String, val email: String){
  def getName: String = {
    lastName + " " + firstName
  }
}
