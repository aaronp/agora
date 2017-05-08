package jabroni.rest.exchange

import akka.stream.scaladsl.{Sink, SinkQueueWithCancel, Source}
import akka.util.ByteString
import io.circe.Json
import io.circe.optics.JsonPath
import jabroni.rest.worker.{WorkContext, WorkerConfig}
import org.reactivestreams.{Publisher, Subscriber, Subscription}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{Matchers, WordSpec}

class RoutingClientTest extends WordSpec with Matchers with ScalaFutures with jabroni.api.Implicits {

  "RoutingClient.handleSource" should {
    "Stream work from workers" in {
      val exConf = ExchangeConfig("port=6666")
      exConf.startExchange().futureValue
      val worker = WorkerConfig("port=7777", "exchange.port=6666").startWorker().futureValue
      import exConf.implicits._

      val subscriptionAckFuture = worker.service.handleSource[Int] { (ctxt: WorkContext[Int]) =>
        def iter = Iterator.continually(ctxt.request).zipWithIndex.map {
          case (offset, n) =>
            val res = offset + n
            ByteString.fromString(res.toString)
        }

        Source.fromIterator(() => iter)
      }
      subscriptionAckFuture.futureValue


      val res: CompletedWork = worker.service.exchange.enqueue(12345.asJob).futureValue
      val values = res.iterateResponse()
      val strings = values.take(100).map(_.decodeString("UTF-8"))
      val stringList = strings.toList
      stringList.head shouldBe "12345"
      stringList.last shouldBe "12444"
      stringList.size shouldBe 100
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

        val subscriptionAckFuture = worker.service.handleJson[Json] { req =>
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
