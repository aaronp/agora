package miniraft


import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model.{HttpEntity, HttpResponse}
import akka.http.scaladsl.server.Directives.{entity, pathPrefix, _}
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.{Decoder, Encoder}
import io.circe.generic.auto._
import jabroni.rest.LoggingSupport

import scala.language.reflectiveCalls

case class RaftRoutes[T: Encoder : Decoder](raftNode: RaftNode)(implicit mat: Materializer)
  extends FailFastCirceSupport
    with LoggingSupport {

  import io.circe.syntax._

  def routes: Route = {
    val all = pathPrefix("rest" / "raft") {
      vote ~ append
    }
    logRoute(all)
  }

  def vote = post {
    (path("vote") & pathEnd) {
      entity(as[RequestVote]) { request =>
        complete {
          val resp = raftNode.handleRequestVote(request)
          HttpResponse(entity = HttpEntity(`application/json`, resp.asJson.noSpaces))
        }
      }
    }
  }

  def append = post {
    (path("append") & pathEnd) {
      entity(as[AppendEntries[T]]) { request =>
        complete {
          val resp = raftNode.handleAppendEntries(request)
          HttpResponse(entity = HttpEntity(`application/json`, resp.asJson.noSpaces))
        }
      }
    }
  }
}
