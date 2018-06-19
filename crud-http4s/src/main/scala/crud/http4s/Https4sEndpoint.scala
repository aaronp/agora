package crud.http4s

import cats.effect.{Effect, IO}
import fs2.StreamApp
import io.circe.Json
import org.http4s.HttpService
import org.http4s.dsl.Http4sDsl
import org.http4s.server.blaze.BlazeBuilder

import scala.concurrent.ExecutionContext

import org.http4s.circe._

class HelloWorldService[F[_]: Effect] extends Http4sDsl[F] {

  val service: HttpService[F] = {
    HttpService[F] {
      case GET -> Root / "hello" / name =>
        Ok(Json.obj("message" -> Json.fromString(s"Hello, ${name}")))
    }
  }
}

object Main extends StreamApp[IO] {

  import scala.concurrent.ExecutionContext.Implicits.global
  override def stream(args: List[String], requestShutdown: IO[Unit]) = Https4sEndpoint.stream[IO]
}

object Https4sEndpoint {

  def stream[F[_]: Effect](implicit ec: ExecutionContext): fs2.Stream[F, StreamApp.ExitCode] = {

    val hws: HttpService[F] = new HelloWorldService[F].service

    val builder: BlazeBuilder[F] = BlazeBuilder[F]
      .bindHttp(8080, "0.0.0.0")
      .mountService(hws, "/")

    builder.serve
  }
}
