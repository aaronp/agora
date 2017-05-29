package jabroni.rest

import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import jabroni.rest.client.RestClient

import scala.concurrent.Future

abstract class BaseRoutesSpec extends BaseSpec with ScalatestRouteTest {

  case class DirectRestClient(r: Route) extends RestClient {
    override def send(request: HttpRequest): Future[HttpResponse] = {
      val res: RouteTestResult = request ~> r
      Future.successful(res.response)
    }

    override def close(): Unit = {}
  }

}
