package agora.rest

import agora.BaseSpec
import agora.rest.client.RestClient
import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.{ActorMaterializer, Materializer}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport

import scala.concurrent.Future

abstract class BaseRoutesSpec extends BaseSpec with ScalatestRouteTest with FailFastCirceSupport {

  private def testMaterializer = materializer
  case class DirectRestClient(r: Route) extends RestClient {
    override def send(request: HttpRequest): Future[HttpResponse] = runRoute(r, request)
    override implicit val materializer: Materializer              = testMaterializer
  }

  private def runRoute(r: Route, request: HttpRequest): Future[HttpResponse] = {
    val res: RouteTestResult = request ~> r
    Future.successful(res.response)
  }

  override def afterAll(): Unit = {
    super.afterAll()
    cleanUp()
  }
}
