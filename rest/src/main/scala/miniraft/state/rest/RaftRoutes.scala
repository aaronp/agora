package miniraft.state.rest

import akka.http.scaladsl.server.Directives.{path, _}
import akka.http.scaladsl.server.Route
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.{Decoder, Encoder}
import miniraft.state.{AppendEntries, RaftEndpoint, RaftNode, RequestVote}

import scala.concurrent.ExecutionContext
import scala.language.reflectiveCalls

case class RaftRoutes[T: Encoder : Decoder](endpoint: RaftEndpoint[T])(implicit ec : ExecutionContext)
  extends RaftJson
    with FailFastCirceSupport {

  def routes: Route = {
    pathPrefix("rest") {
      pathPrefix("raft") {
        requestVote ~ appendEntities
      }
    }
  }

  def requestVote = (post & path("vote") & pathEnd) {
    entity(as[RequestVote]) { vote =>
      complete {
        endpoint.onVote(vote)
      }
    }
  }

  def appendEntities = (post & path("append") & pathEnd) {
    entity(as[AppendEntries[T]]) { ae =>
      complete {
        endpoint.onAppend(ae)
      }
    }
  }
}
