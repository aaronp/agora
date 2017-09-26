package miniraft.state.rest

import akka.http.scaladsl.server.Directives.{path, _}
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.{Decoder, Encoder}
import miniraft.{AppendEntries, RaftEndpoint, RequestVote}

import scala.concurrent.ExecutionContext
import scala.language.reflectiveCalls

/**
  * Routes required to support the raft protocol as a communication between raft nodes.
  *
  * For client or support routes, well, look elsewhere. Please.
  *
  * @param endpoint
  * @param ev$1
  * @param ev$2
  * @param ec
  * @tparam T
  */
case class RaftRoutes[T: Encoder: Decoder](endpoint: RaftEndpoint[T])(implicit ec: ExecutionContext)
    extends RaftJson
    with FailFastCirceSupport
    with StrictLogging {

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
    extractExecutionContext { implicit ec =>
      entity(as[AppendEntries[T]]) { ae =>
        complete {
          val future = endpoint.onAppend(ae)
          ae.entry.foreach { entry =>
            future.onComplete { result =>
              logger.info(s"Append ${entry} returning $result")
            }
          }

          future
        }
      }
    }
  }

}
