package controllers

import javax.inject._
import play.api.mvc._
import play.api.libs.json._
import models._
import slick.jdbc.MySQLProfile.api._
import scala.concurrent.{ExecutionContext, Future}
import pdi.jwt._
import org.mindrot.jbcrypt.BCrypt
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile

@Singleton
class AuthController @Inject()(protected val dbConfigProvider: DatabaseConfigProvider, cc: ControllerComponents)(implicit ec: ExecutionContext)
  extends AbstractController(cc) with HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api._

  def register = Action.async(parse.json) { request =>
    request.body.validate[User].fold(
      errors => Future.successful(BadRequest(Json.obj("status" -> "error", "message" -> JsError.toJson(errors)))),
      user => {
        val hashedPassword = BCrypt.hashpw(user.password, BCrypt.gensalt())
        val newUser = user.copy(password = hashedPassword)

        db.run(Users.users += newUser).map { _ =>
          Ok(Json.obj("status" -> "success"))
        }.recover {
          case ex: Exception => InternalServerError(Json.obj("status" -> "error", "message" -> ex.getMessage))
        }
      }
    )
  }

  def login = Action.async(parse.json) { request =>
    request.body.validate[User].fold(
      errors => Future.successful(BadRequest(Json.obj("status" -> "error", "message" -> JsError.toJson(errors)))),
      credentials => {
        db.run(Users.users.filter(_.username === credentials.username).result.headOption).map {
          case Some(user) if BCrypt.checkpw(credentials.password, user.password) =>
           System.out.println("user.password: "+user.password)
            System.out.println("credentials.password: "+credentials.password)
            //print username
            System.out.println("user.username: "+user.username)
            val token = JwtJson.encode(Json.obj("username" -> user.username), "secretKey", JwtAlgorithm.HS256)
            Ok(Json.obj("token" -> token))
          case _ => Unauthorized(Json.obj("status" -> "error", "message" -> "Invalid credentials"))
        }.recover {
          case ex: Exception => InternalServerError(Json.obj("status" -> "error", "message" -> ex.getMessage))
        }
      }
    )
  }

  def protectedEndpoint = Action.async { request =>
  request.headers.get("Authorization").flatMap { token =>
    // Print the received token
    //remove initila Bearer from token
    val tokenClear = token.substring(7)
    System.out.println("token: "+tokenClear)
    println(JwtJson.decodeJson(tokenClear,"secretKey",Seq(JwtAlgorithm.HS256)))
    JwtJson.decodeJson(tokenClear, "secretKey", Seq(JwtAlgorithm.HS256)).toOption

  } match {
    case Some(json) =>
      // Print the decoded token
      println(s"Decoded token: $json")
      // Token is valid, proceed with the request
      Future.successful(Ok(Json.obj("status" -> "success", "message" -> "Access granted")))
    case None =>
      // Token is invalid or missing
      Future.successful(Unauthorized(Json.obj("status" -> "error", "message" -> "Invalid token")))
  }
}
}