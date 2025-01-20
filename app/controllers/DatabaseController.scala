package controllers

import javax.inject._
import play.api.mvc._
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile
import scala.concurrent.{ExecutionContext, Future}

@Singleton
class DatabaseController @Inject()(protected val dbConfigProvider: DatabaseConfigProvider, cc: ControllerComponents)(implicit ec: ExecutionContext)
  extends AbstractController(cc) with HasDatabaseConfigProvider[JdbcProfile] {

  import profile.api._

  def testConnection: Action[AnyContent] = Action.async {
    val testQuery = sql"SELECT 1".as[Int].headOption
    db.run(testQuery).map {
      case Some(_) => Ok("Database connection is working: 1")
      case None => InternalServerError("Database connection failed")
    }.recover {
      case ex: Exception => InternalServerError(s"Database connection failed: ${ex.getMessage}")
    }
  }
}