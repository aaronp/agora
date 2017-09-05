package agora.rest

import agora.api.BaseSpec
import agora.rest.client.RestClient
import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.testkit.ScalatestRouteTest
import akka.stream.{ActorMaterializer, Materializer}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport

import scala.concurrent.Future

abstract class BaseRoutesSpec extends BaseSpec with ScalatestRouteTest with FailFastCirceSupport {

  case class DirectRestClient(r: Route) extends RestClient {
    override def send(request: HttpRequest): Future[HttpResponse] = runRoute(r, request)

    val system                                       = ActorSystem(getClass.getName.filter(_.isLetter))
    override implicit val materializer: Materializer = ActorMaterializer.create(system)
  }

  private def runRoute(r: Route, request: HttpRequest): Future[HttpResponse] = {
    val res: RouteTestResult = request ~> r
    Future.successful(res.response)
  }

}
