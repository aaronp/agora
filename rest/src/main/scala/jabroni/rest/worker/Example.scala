package jabroni.rest.worker

import jabroni.rest.client.{ClientConfig, RestClient}

import scala.concurrent.Future

object Example extends App {
  import io.circe.generic.auto._

  case class DoubleMe(x: Int)

  case class Doubled(x: Int)

  val routes = ClientConfig().workerRoutes

  routes.handle { ctxt: WorkContext[DoubleMe] =>
    val doubled = ctxt.request.x * ctxt.request.x
    ctxt.take(1)
    Doubled(doubled)
  }

  val future: Future[Worker.RunningService] = Worker.runWith(routes)

}
