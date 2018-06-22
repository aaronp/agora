package crud.http4s

import java.io.File

import cats.data.OptionT
import cats.effect.{Effect, IO}
import fs2.StreamApp
import io.circe.Json
import monix.eval.Task
import org.http4s.{HttpService, Request, Response, StaticFile}
import org.http4s.dsl.Http4sDsl
import org.http4s.server.blaze.BlazeBuilder
import org.http4s.server.websocket._

import scala.concurrent.ExecutionContext
import org.http4s.circe._

class HelloWorldService[F[_]: Effect] extends Http4sDsl[F] {

  def static(file: String, request: Request[F]) =
    StaticFile.fromResource("/" + file, Some(request)).map(Task.now).getOrElse(NotFound())

  val service: HttpService[F] = {
    HttpService[F] {
      case GET -> Root / "hello" / name =>
        Ok(Json.obj("message" -> Json.fromString(s"Hello, ${name}")))

      case request @ GET -> Root / "index.html" =>
        val filestream: OptionT[F, Response[F]] = StaticFile
          .fromFile(new File("relative/path/to/index.html"), Some(request))


        def notFound : Response[F] = {
          NotFound()
          ???
        }
        filestream//.map(Task.now) // This one is require to make the types match up
          .getOrElse(notFound) // In case the file doesn't exist

    }
  }
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
