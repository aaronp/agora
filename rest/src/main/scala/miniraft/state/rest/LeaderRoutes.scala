package miniraft.state.rest

import akka.http.scaladsl.model.headers.Location
import akka.http.scaladsl.model.{HttpResponse, StatusCodes, Uri}
import akka.http.scaladsl.server.Directives.{path, _}
import akka.http.scaladsl.server.Route
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.{Decoder, Encoder}
import agora.api.worker.HostLocation
import miniraft.LeaderApi
import miniraft.state._

import scala.concurrent.{ExecutionContext, Future}
import scala.language.reflectiveCalls

/**
  * Routes to allow client calls to append to the state machine
  *
  * @param node
  * @param locationForId
  * @param teaFromString
  * @param ev$1
  * @param ev$2
  * @param ec
  * @tparam T
  */
case class LeaderRoutes[T: Encoder: Decoder](node: RaftNode[T], leader: LeaderApi[T], locationForId: NodeId => HostLocation, teaFromString: String => T)(
    implicit ec: ExecutionContext)
    extends RaftJson
    with FailFastCirceSupport {

  def routes: Route = {
    pathPrefix("rest") {
      pathPrefix("raft") {
        pathPrefix("leader") {
          appendGet ~ appendPost
        }
      }
    }
  }

  def appendGet = (get & path("append") & pathEnd) {
    parameter('text) { text =>
      complete {
        doAppend(text)
      }
    }
  }

  def appendPost: Route = (post & path("append") & pathEnd) {
    entity(as[String]) { text =>
      complete {
        doAppend(text)
      }
    }
  }

  private def doAppend(text: String): Future[HttpResponse] = {
    val tea = teaFromString(text)
    val response: Future[HttpResponse] = leader(tea).flatMap(_.result).map {
      case true  => HttpResponse(StatusCodes.Created)
      case false => HttpResponse(StatusCodes.InternalServerError)
    }

    response.recover {
      case NotTheLeaderException(Some(leaderId), _, _, _) =>
        val sendTo   = locationForId(leaderId)
        val uri: Uri = sendTo.asURL + "/rest/raft/leader/append"
        HttpResponse(status = StatusCodes.TemporaryRedirect, headers = List(Location(uri)))
      case NotTheLeaderException(None, id, state, clusterSize) =>
        HttpResponse(
          StatusCodes
            .custom(550, s"$id is currently in state '$state', and there currently is no leader", s"None of the ${clusterSize} nodes are in the leader state.", false, true))
    }
  }

}
