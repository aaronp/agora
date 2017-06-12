package jabroni.rest.exchange

import akka.stream.scaladsl.Source
import akka.util.ByteString
import io.circe.Json
import io.circe.optics.JsonPath
import jabroni.api.exchange.QueuedStateResponse
import jabroni.integration.BaseIntegrationTest
import jabroni.rest.worker.WorkContext

import scala.language.reflectiveCalls

trait RoutingClientTest extends jabroni.api.Implicits {
  self: BaseIntegrationTest =>

  "RoutingClient.handleSource" should {
    "Stream work from workers" in {

      val subscriptionAckFuture = worker.service.addHandler[Int] { (ctxt: WorkContext[Int]) =>
        def iter = Iterator.continually(ctxt.request).zipWithIndex.map {
          case (offset, n) =>
            val res = offset + n
            ByteString.fromString(res.toString)
        }

        ctxt.completeWithSource(Source.fromIterator(() => iter))
      }
      subscriptionAckFuture.futureValue

      val res: CompletedWork = exchangeClient.enqueue(12345.asJob).futureValue
      val values = res.iterateResponse(testTimeout)
      val strings = values.take(100).map(_.decodeString("UTF-8"))
      val stringList = strings.toList
      stringList.head shouldBe "12345"
      stringList.last shouldBe "12444"
      stringList.size shouldBe 100
    }
    "Route match responses to workers" in {
      // the path to our echoed response
      val gotPath = JsonPath.root.got.string

      var workerRequests = List[Json]()

      // verify preconditions

      val initialQueue: QueuedStateResponse = exchangeClient.queueState().futureValue
      initialQueue.isEmpty shouldBe true

      /**
        * Add a worker which will reply to work with 'got : XYZ'
        */
      val subscriptionAckFuture = worker.service.addHandler[Json] { req =>
        workerRequests = req.request :: workerRequests
        req.completeWithJson(Json.obj("got" -> req.request))
      }
      val subscriptionKey = subscriptionAckFuture.futureValue.id

      // verify the queue is as we expected
      val queue = exchangeClient.queueState().futureValue
      queue.subscriptions.size shouldBe 1
      val List(ourSubscription) = queue.subscriptions
      ourSubscription.key shouldBe subscriptionKey
      ourSubscription.requested shouldBe worker.service.defaultInitialRequest

      val job = "hello world!".asJob
      val resp = exchangeClient.enqueue(job).futureValue
      resp.onlyWorker.subscriptionKey shouldBe subscriptionKey
      val Right(jsonResp) = resp.jsonResponse.futureValue
      val found = gotPath.getOption(jsonResp)
      val actual = found.get
      actual shouldBe "hello world!"
    }
  }
}
