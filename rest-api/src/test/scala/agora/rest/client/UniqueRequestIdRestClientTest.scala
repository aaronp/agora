package agora.rest.client

import agora.BaseRestApiSpec
import agora.rest.HasMaterializer
import agora.rest.client.header._
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.stream.Materializer

import scala.concurrent.Future

class UniqueRequestIdRestClientTest extends BaseRestApiSpec with HasMaterializer {

  "UniqueRequestIdRestClient" should {
    "send a unique request id w/ each request" in {
      val clientUnderTest = new UniqueRequestIdRestClient(TestClient, "some user or whatever", 123)

      // call the method under test - it should stamp an ID on
      val expectedCount = (0 to 3).count { _ =>
        clientUnderTest.send(HttpRequest()).futureValue
        true
      }

      TestClient.idsSends.distinct.size shouldBe expectedCount
    }
  }

  def testMaterializer = materializer

  object TestClient extends RestClient {
    var requests = List[HttpRequest]()
    var idsSends = List[String]()

    override def send(request: HttpRequest): Future[HttpResponse] = {
      requests = request :: requests
      val reqIdOpt = request.headers.collectFirst {
        case `X-Request-Id`(id) => id
      }
      idsSends = reqIdOpt.toList ++ idsSends
      Future.successful(HttpResponse())
    }

    override implicit def materializer: Materializer = testMaterializer

    override def stop(): Future[Any] = Future.successful("ok")
  }

}
