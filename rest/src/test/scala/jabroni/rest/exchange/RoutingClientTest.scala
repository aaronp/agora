package jabroni.rest.exchange

import akka.stream.scaladsl.Source
import akka.util.ByteString
import io.circe.Json
import io.circe.optics.JsonPath
import jabroni.rest.worker.WorkerConfig.RunningWorker
import jabroni.rest.worker.{WorkContext, WorkerConfig}
import jabroni.rest.{BaseSpec, RunningService}
import org.scalatest.time.{Millis, Seconds, Span}

import scala.language.reflectiveCalls

class RoutingClientTest extends BaseSpec with jabroni.api.Implicits {

  "RoutingClient.handleSource" should {
    "Stream work from workers" in {
      val exchangePort = 9000
      val workerPort = exchangePort + 1
      val exConf = ExchangeConfig(s"port=$exchangePort")
      val running: RunningService[ExchangeConfig, ExchangeRoutes] = exConf.startExchange().futureValue
      val workerConf = WorkerConfig(s"port=$workerPort", s"exchange.port=$exchangePort")
      val worker: RunningWorker = workerConf.startWorker().futureValue
      try {
        import exConf.implicits._

        val subscriptionAckFuture = worker.service.addHandler[Int] { (ctxt: WorkContext[Int]) =>
          def iter = Iterator.continually(ctxt.request).zipWithIndex.map {
            case (offset, n) =>
              val res = offset + n
              ByteString.fromString(res.toString)
          }

          ctxt.completeWithSource(Source.fromIterator(() => iter))
        }
        subscriptionAckFuture.futureValue


        val res: CompletedWork = workerConf.exchangeClient.enqueue(12345.asJob).futureValue
        val values = res.iterateResponse(testTimeout)
        val strings = values.take(100).map(_.decodeString("UTF-8"))
        val stringList = strings.toList
        stringList.head shouldBe "12345"
        stringList.last shouldBe "12444"
        stringList.size shouldBe 100
      } finally {
        running.stop()
        worker.stop()
      }
    }
    "Route match responses to workers" in {
      // the path to our echoed response
      val gotPath = JsonPath.root.got.string
      // start an exchange server
      val exchange = ExchangeConfig().startExchange().futureValue

      try {

        // start a worker
        val workerConf = WorkerConfig()
        val worker = workerConf.startWorker().futureValue

        var workerRequests = List[Json]()

        val subscriptionAckFuture = worker.service.addHandler[Json] { req =>
          workerRequests = req.request :: workerRequests
          req.completeWithJson(Json.obj("got" -> req.request))
        }
        val subscriptionKey = subscriptionAckFuture.futureValue.id

        // nick the exchange client from the worker to submit some jobs
        val exchangeClient: ExchangeClient = workerConf.exchangeClient

        val job = "hello world!".asJob
        val resp = exchangeClient.enqueue(job).futureValue
        resp.onlyWorker.subscriptionKey shouldBe subscriptionKey
        val Right(jsonResp) = resp.jsonResponse.futureValue
        val found = gotPath.getOption(jsonResp)
        val actual = found.get
        actual shouldBe "hello world!"

        worker.stop()
      } finally {
        exchange.stop()
      }
    }
  }

  implicit override val patienceConfig =
    PatienceConfig(timeout = scaled(Span(5, Seconds)), interval = scaled(Span(50, Millis)))
}
