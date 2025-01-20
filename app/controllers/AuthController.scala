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
import java.time.Instant
import java.time.temporal.ChronoUnit

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
          val accessTokenExpiration = Instant.now.plus(1, ChronoUnit.HOURS).getEpochSecond
          val refreshTokenExpiration = Instant.now.plus(30, ChronoUnit.DAYS).getEpochSecond

          val accessToken = JwtJson.encode(
            Json.obj(
              "username" -> user.username,
              "exp" -> accessTokenExpiration
            ),
            "secretKey",
            JwtAlgorithm.HS256
          )

          val refreshToken = JwtJson.encode(
            Json.obj(
              "username" -> user.username,
              "exp" -> refreshTokenExpiration
            ),
            "secretKey",
            JwtAlgorithm.HS256
          )

          Ok(Json.obj("accessToken" -> accessToken, "refreshToken" -> refreshToken))
        case _ => Unauthorized(Json.obj("status" -> "error", "message" -> "Invalid credentials"))
      }.recover {
        case ex: Exception => InternalServerError(Json.obj("status" -> "error", "message" -> ex.getMessage))
      }
    }
  )
}

def refreshToken = Action.async(parse.json) { request =>
  request.body.validate[JsObject].fold(
    errors => Future.successful(BadRequest(Json.obj("status" -> "error", "message" -> JsError.toJson(errors)))),
    json => {
      (json \ "refreshToken").asOpt[String] match {
        case Some(refreshToken) =>
          JwtJson.decodeJson(refreshToken, "secretKey", Seq(JwtAlgorithm.HS256)).toOption match {
            case Some(decodedJson) =>
              val username = (decodedJson \ "username").as[String]
              val expiration = (decodedJson \ "exp").as[Long]
              if (Instant.now.getEpochSecond > expiration) {
                Future.successful(Unauthorized(Json.obj("status" -> "error", "message" -> "Refresh token expired")))
              } else {
                // Generate a new Access Token
                val newAccessToken = JwtJson.encode(
                  Json.obj("username" -> username, "exp" -> (Instant.now.plus(1, ChronoUnit.HOURS).getEpochSecond)),
                  "secretKey",
                  JwtAlgorithm.HS256
                )
                Future.successful(Ok(Json.obj("accessToken" -> newAccessToken)))
              }
            case None => Future.successful(Unauthorized(Json.obj("status" -> "error", "message" -> "Invalid refresh token")))
          }
        case None => Future.successful(BadRequest(Json.obj("status" -> "error", "message" -> "Refresh token missing")))
      }
    }
  )
}

  def protectedEndpoint = Action.async { request =>
  request.headers.get("Authorization").flatMap { token =>
    // Remove "Bearer " prefix from token
    val tokenClear = token.substring(7)
    println(s"Received token: $tokenClear")
    JwtJson.decodeJson(tokenClear, "secretKey", Seq(JwtAlgorithm.HS256)).toOption
  } match {
    case Some(json) =>
      val expiration = (json \ "exp").as[Long]
      if (Instant.now.getEpochSecond > expiration) {
        Future.successful(Unauthorized(Json.obj("status" -> "error", "message" -> "Token expired")))
      } else {
        // Print the decoded token
        println(s"Decoded token: $json")
        // Token is valid, proceed with the request
        Future.successful(Ok(Json.obj("status" -> "success", "message" -> "Access granted")))
      }
    case None =>
      // Token is invalid or missing
      Future.successful(Unauthorized(Json.obj("status" -> "error", "message" -> "Invalid token")))
  }
}
}