package agora.rest.client

import agora.BaseRestApiSpec
import agora.rest.AkkaImplicits
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.Materializer

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.util.Try

class RetryClientTest extends BaseRestApiSpec {

  "RetryClient.send" should {
    "retry when a connection errors and close the previous connection" in {

      val strategy: RetryStrategy.CountingStrategy = RetryStrategy.tolerate(3).failuresWithin(5.seconds).withDelay(100.millis)

      var badClients = List[FailingClient]()
      val retry = RetryClient(strategy) { () =>
        val c = new FailingClient
        badClients = c :: badClients
        c
      }

      try {
        badClients shouldBe empty

        // try to send something - it should create a new client and close the old one
        Try(Await.result(retry.send(HttpRequest()), testTimeout))

        badClients.size shouldBe 4

        val futures = badClients.map { fc =>
          fc.stopCalled shouldBe true
          fc.stop()
        }

        import scala.concurrent.ExecutionContext.Implicits.global
        Future.sequence(futures).futureValue
      } finally {
        retry.stop().futureValue
      }

    }
  }

  class FailingClient extends RestClient {
    val implicits = AkkaImplicits("failing", conf"""akka.daemonic : true""")

    override def send(request: HttpRequest): Future[HttpResponse] = {
      Future.failed(new Exception("bang"))
    }

    override implicit def materializer: Materializer = implicits.materializer

    var stopCalled = false
    override def stop(): Future[Any] = {
      stopCalled = true
      implicits.stop()
    }
  }

}
