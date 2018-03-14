package agora.rest.client

import agora.BaseRestApiSpec
import agora.api.data.Lazy
import agora.rest.AkkaImplicits
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.Materializer
import com.typesafe.config.ConfigFactory

import scala.concurrent.Future
import scala.concurrent.duration._

class RoundRobinClientTest extends BaseRestApiSpec {
  "RoundRobinClient.send" should {
    "cycle through the specified hosts when one fails" in {
      val clients = List.fill(4)(new TestClient)
      val rrc     = new RoundRobinClient(clients, RetryStrategy.throttle(1.millis))
      try {
        rrc.send(HttpRequest())
        rrc.send(HttpRequest())
        rrc.send(HttpRequest())

        clients.head.sentCount shouldBe 3
      } finally {
        rrc.stop().futureValue
      }
    }
  }

  class TestClient extends RestClient {
    val sys       = Lazy(AkkaImplicits(getClass.getSimpleName, ConfigFactory.empty))
    var closed    = false
    var sentCount = 0

    var nextResponse = Future.successful(HttpResponse())

    override def send(request: HttpRequest): Future[HttpResponse] = {
      sentCount += 1
      nextResponse
    }

    override implicit def materializer: Materializer = sys.value.materializer

    override def stop(): Future[Any] = {
      closed = true
      sys.foreach(_.stop().futureValue)
      Future.successful("ok")
    }
  }

}
