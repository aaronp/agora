package jabroni.rest.exchange

import io.circe.Json
import io.circe.optics.JsonPath
import jabroni.rest.worker.WorkerConfig
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{Matchers, WordSpec}

class RoutingClientTest extends WordSpec with Matchers with ScalaFutures with jabroni.api.Implicits {

  "RoutingClient" should {
    "Stream work to workers" in {
      val exchange = ExchangeConfig("port=6666").startExchange().futureValue
      val worker = WorkerConfig("port=666").startWorker().futureValue

      val subscriptionAckFuture = worker.service.handle { ctxt =>
        ctxt.request

      }
    }
    "Route match responses to workers" in {
      // the path to our echoed response
      val gotPath = JsonPath.root.got.string
      // start an exchange server
      val exchange = ExchangeConfig().startExchange().futureValue

      try {

        // start a worker
        val worker = WorkerConfig().startWorker().futureValue

        var workerRequests = List[Json]()

        val subscriptionAckFuture = worker.service.handleJson { req =>
          workerRequests = req.request :: workerRequests
          req.take(1) // as per usual, request one after we've processed one ... though in this case there won't be any more
          Json.obj("got" -> req.request)
        }
        val subscriptionKey = subscriptionAckFuture.futureValue.id

        // nick the exchange client from the worker to submit some jobs
        val exchangeClient: ExchangeClient = worker.service.exchange

        val job = "hello world!".asJob
        val resp = exchangeClient.enqueue(job).futureValue
        resp.onlyWorker.subscriptionKey shouldBe subscriptionKey
        val Right(jsonResp) = resp.jsonResponse.futureValue
        val found = gotPath.getOption(jsonResp)
        found.get shouldBe job.job

        worker.stop()
      } finally {
        exchange.stop()
      }
    }
  }

  implicit override val patienceConfig =
    PatienceConfig(timeout = scaled(Span(5, Seconds)), interval = scaled(Span(50, Millis)))
}
