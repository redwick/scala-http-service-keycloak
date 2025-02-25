package server.http

import io.circe.{Decoder, Encoder, HCursor, Json}

object RealmGroup{
  implicit val decodeGroup: Decoder[RealmGroup] = new Decoder[RealmGroup] {
    final def apply(c: HCursor): Decoder.Result[RealmGroup] =
      for {
        id <- c.downField("id").as[String]
        name <- c.downField("name").as[String]
      } yield {
        new RealmGroup(id, name)
      }
  }
  implicit val encodeUser: Encoder[RealmGroup] = new Encoder[RealmGroup] {
    final def apply(a: RealmGroup): Json = Json.obj(
      ("id", Json.fromString(a.id)),
      ("name", Json.fromString(a.name)),
    )
  }
  def empty = new RealmGroup("", "")
}
class RealmGroup(val id: String, val name: String)
