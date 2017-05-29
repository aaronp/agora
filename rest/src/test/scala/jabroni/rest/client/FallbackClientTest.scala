package jabroni.rest.client

import java.util.concurrent.atomic.AtomicInteger

import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import jabroni.rest.BaseSpec

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits._

class FallbackClientTest extends BaseSpec with RequestBuilding {

  "FallbackClient" should {
    "fail if no clients are given" in {
      val exp = intercept[Exception] {
        Await.result(new FallbackClient(Nil).send(Get()), 1.second)
      }
      exp.getMessage should include("No clients left after 0 failures")
    }
    "retry until a successful client is used" in {
      val createdCount = new AtomicInteger(0)
      class TestClient(succeed: Boolean) extends RestClient {
        createdCount.incrementAndGet()

        override def send(request: HttpRequest): Future[HttpResponse] = if (succeed) {
          Future.successful(HttpResponse())
        } else {
          Future.failed(new Exception("bang!"))
        }
      }
      val clients = Stream(false, true, true).map(succeed => new TestClient(succeed))
      createdCount.get shouldBe 1
      new FallbackClient(clients).send(Get()).futureValue
      createdCount.get shouldBe 2
    }
  }

}
