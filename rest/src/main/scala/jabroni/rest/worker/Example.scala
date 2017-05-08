package jabroni.rest.worker

import com.typesafe.scalalogging.StrictLogging
import jabroni.api._
import jabroni.api.exchange.{ClientResponse, SubmitJobResponse}
import jabroni.rest.RunningService
import jabroni.rest.exchange.{CompletedWork, ExchangeClient, ExchangeConfig, ExchangeRoutes}
import jabroni.rest.worker.Example.exchange
import jabroni.rest.worker.WorkerConfig.RunningWorker

import scala.collection.immutable
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.reflectiveCalls
import scala.util.{Failure, Success, Try}


object Example extends App with StrictLogging {

  case class DoubleMe(x: Int)

  case class Doubled(x: Int)

  import io.circe.generic.auto._

  def compute(ctxt: WorkContext[DoubleMe]): Doubled = {
    val doubled = ctxt.request.x * ctxt.request.x
    logger.info(s"${ctxt.path} calculating ${ctxt.request}")
    Thread.sleep(100)
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

  // start an exchange:
  val exchangeServerFuture: Future[RunningService[ExchangeConfig, ExchangeRoutes]] = ExchangeConfig().startExchange()
  Await.result(exchangeServerFuture, 5 seconds)


  // start some workers
  val workerFutures = (0 to 4).map { i =>
    startWorker(i)
  }

  val workers: immutable.Seq[RunningWorker] = Await.result(Future.sequence(workerFutures), 10.seconds)

  // create a client of the exchange ... here we just nick one of the worker's clients
  val exchange: ExchangeClient = workers.head.service.exchange

  import Implicits._

  val results = (0 to 2).map { input =>
    val job = DoubleMe(input).asJob
    val resp = exchange.enqueue(job).flatMap(_.jsonResponse)
    resp.onComplete {
      case Success(work) =>
        println(work)
      case Failure(err) =>

        println(err)
    }
    resp
  }


  println(results)

}
