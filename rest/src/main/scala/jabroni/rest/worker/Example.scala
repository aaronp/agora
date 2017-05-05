package jabroni.rest.worker

import scala.concurrent.ExecutionContext.Implicits._

object Example extends App {

  case class DoubleMe(x: Int)

  case class Doubled(x: Int)

  import io.circe.generic.auto._


  Worker.start(args).map { running =>
    val routes = running.service.workerRoutes

    routes.handle { ctxt: WorkContext[DoubleMe] =>
      val doubled = ctxt.request.x * ctxt.request.x
      ctxt.take(1)
      Doubled(doubled)
    }
  }

}
