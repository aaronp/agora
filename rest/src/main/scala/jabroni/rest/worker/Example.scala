package jabroni.rest.worker

import com.typesafe.scalalogging.StrictLogging
import jabroni.api._
import jabroni.api.exchange.{ClientResponse, SubmitJobResponse}
import jabroni.rest.worker.WorkerConfig.RunningWorker

import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.reflectiveCalls


object Example extends App with StrictLogging {

  case class DoubleMe(x: Int)

  case class Doubled(x: Int)

  import io.circe.generic.auto._

  def compute(ctxt: WorkContext[DoubleMe]): Doubled = {
    val doubled = ctxt.request.x * ctxt.request.x
    logger.info(s"${ctxt.path} calculating ${ctxt.request}")
    Thread.sleep(1000)
    ctxt.take(1)
    Doubled(doubled)
  }

  def startWorker(portOffset: Int): Future[RunningWorker] = {
    val port = 5000 + portOffset
    val name = s"worker-$portOffset"
    val conf = WorkerConfig(s"details.path=$name" +: s"port=$port" +: args)
    for {
      running: RunningWorker <- conf.startWorker()
      _ <- conf.workerRoutes.handle(compute)
    } yield {
      running
    }
  }


  // start some workers
//  val workerFutures2 = (0 to 4).map { i =>
//    startWorker(i)
//  }
  val workerFutures = List(startWorker(100))

  val workers = Await.result(Future.sequence(workerFutures), 10.seconds)

  val exchange = workers.head.service.exchange

  import Implicits._

  (0 to 2).map { input =>
    val job = DoubleMe(input).asJob
    val resp: Future[ClientResponse] = exchange.submit(job)
    resp.onComplete {
      case acked =>

        println(acked)
    }
  }


}
