package crud.http4s

import cats.effect.IO
import fs2.StreamApp

object Main extends StreamApp[IO] {

  import scala.concurrent.ExecutionContext.Implicits.global
  override def stream(args: List[String], requestShutdown: IO[Unit]) = Https4sEndpoint.stream[IO]
}
