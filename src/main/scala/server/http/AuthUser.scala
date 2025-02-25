package server.http

case class AuthUser(active: Boolean = false, id: String = "", userName: String = "", email: String = "", name: String = "", roles: List[String] = List.empty[String])
