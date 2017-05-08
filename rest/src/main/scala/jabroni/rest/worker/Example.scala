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
import Implicits._

object Example extends App with StrictLogging {

  case class DoubleMe(x: Int)

  case class Doubled(x: Int)

  import io.circe.generic.auto._

  // define some computation. Instead of just
  // f = DoubleMe => Doubled
  // we have 
  // f = WorkContext[DoubleMe] => Doubled
  // where the 'WorkContext' holds things like the exchange client, subscription key, match details (e.g. job and worker ids), etc
  def compute(ctxt: WorkContext[DoubleMe]): Doubled = {
    val doubled = ctxt.request.x * 2
    logger.info(s"${ctxt.path} calculating ${ctxt.request}")
    Thread.sleep(1000) // make believe this is a longer computation

    // we know we've completed one work item now, so we're going to ask for more work
    // this can be done obviously at any time, and depends on whatever the worker is doing
    // e.g. the compute calculation know's when it's done the computationally hard bit, 
    // so it can decide when it asks for more work (or even what sort of work it wants to 
    // ask for)
    ctxt.take(1) 

    Doubled(doubled)
  }

  // now start an exchange endpoint ...
  val exchangeServerFuture: Future[RunningService[ExchangeConfig, ExchangeRoutes]] = ExchangeConfig().startExchange()
  Await.result(exchangeServerFuture, 5 seconds)


  // ... and connect start some workers
  val workerFutures = (0 to 4).map { i =>
    startWorker(i)
  }
  val workers: immutable.Seq[RunningWorker] = Await.result(Future.sequence(workerFutures), 10.seconds)

  // and double some numbers!
  // here we just nick one of the worker's exchange clients, 
  // but we could just as easily connect to the exchange from elsewhere (e.g. a non-worker)
  val exchange: ExchangeClient = workers.head.service.exchange

  (0 to 100).foreach { input =>
    
    val job = DoubleMe(input).asJob // asJob converts some 'T' to a SubmitJob which holds that T as a json request

    // 'enqueue' is a form of submit job ... redirect which returns the worker's response, not the exchange's,
    // so we can do things like ask for the worker's 'jsonResponse'
    val resp = exchange.enqueue(job).flatMap(_.jsonResponse)
    resp.onComplete {
      case Success(work) =>
        println(work)
      case Failure(err) => throw err
    }
  }


  def startWorker(portOffset: Int): Future[RunningWorker] = {
    val port = 5000 + portOffset
    val name = s"worker-$portOffset"
    val conf = WorkerConfig(s"details.path=$name" +: s"port=$port" +: args)
    for {
      running: RunningWorker <- conf.startWorker()
      // add our computation handler to the worker. 
      // We can dynamically add/remove handlers at any time
      _ <- conf.workerRoutes.handle(compute)
    } yield {
      running
    }
  }
}
