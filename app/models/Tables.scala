package models

import slick.jdbc.MySQLProfile.api._
import play.api.libs.json._

case class User(id: Option[Long], username: String, password: String)

class Users(tag: Tag) extends Table[User](tag, "users") {
  def id = column[Long]("id", O.PrimaryKey, O.AutoInc)
  def username = column[String]("username")
  def password = column[String]("password")

  def * = (id.?, username, password) <> ((User.apply _).tupled, User.unapply)
}

object Users {
  val users = TableQuery[Users]
}

object User {
  implicit val userFormat: OFormat[User] = Json.format[User]
}