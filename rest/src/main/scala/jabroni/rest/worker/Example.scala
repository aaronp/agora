package jabroni.rest.worker

import jabroni.api.exchange.{RequestWorkAck, SubmitJob, SubmitJobResponse}
import jabroni.api._

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

object Example extends App {

  case class DoubleMe(x: Int)

  case class Doubled(x: Int)

  import io.circe.generic.auto._


  // start a worker
  val started: Future[(RequestWorkAck, Worker.RunningService)] = Worker.start(args).flatMap { (running: Worker.RunningService) =>
    val routes = running.service.workerRoutes

    val ack = routes.handle { ctxt: WorkContext[DoubleMe] =>
      val doubled = ctxt.request.x * ctxt.request.x
      ctxt.take(1)
      Doubled(doubled)
    }


    ack.map { x =>
      x -> running
    }
  }


  started.map {
    case (ack, svc) =>
      val exchange = svc.service.exchange


      import Implicits._

      val job = DoubleMe(123).asJob
      val resp: Future[SubmitJobResponse] = exchange.submit(job)
      resp.onComplete {
        case acked =>

          println(acked)

      }
  }

  // start a client


}
