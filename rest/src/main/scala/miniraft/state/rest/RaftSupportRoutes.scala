package miniraft.state.rest

import java.nio.file.Path

import akka.http.scaladsl.server.Directives.{path, _}
import akka.http.scaladsl.server.Route
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json, ParsingFailure}
import miniraft.state.RaftNode.async.RaftNodeActorClient
import miniraft.state._

import agora.io.implicits._
import scala.concurrent.ExecutionContext
import scala.language.reflectiveCalls

/**
  * Additional routes not required for Raft but which facilitate development/support/debugging, e.g. by providing
  * for examining the current Raft state
  *
  */
case class RaftSupportRoutes[T: Encoder: Decoder](logic: RaftNodeLogic[T], cluster: ClusterProtocol, messageLogDir: Option[Path])(implicit ec: ExecutionContext)
    extends RaftJson
    with FailFastCirceSupport {

  private def innerRoutes = {
    val all = getState ~ getLogs ~ forceElectionTimeout

    messageLogDir.fold(all) { dir =>
      all ~ getMessages(dir)
    }
  }

  def routes: Route = {
    pathPrefix("rest") {
      pathPrefix("raft") {
        pathPrefix("support") {
          innerRoutes
        }
      }
    }
  }

  def forceElectionTimeout = (get & path("timeout") & pathEnd) {
    import concurrent.duration._

    cluster.electionTimer.reset(Some(0.millis))
    complete {
      NodeStateSummary(logic, cluster).map(_.asJson)
    }
  }

  def getState = (get & path("state") & pathEnd) {
    complete {
      NodeStateSummary(logic, cluster).map(_.asJson)
    }
  }

  def getLogs = (get & path("logs") & pathEnd) {
    parameter('from.?, 'to.?) { (fromOpt, toOpt) =>
      complete {
        val from  = fromOpt.map(_.toInt).getOrElse(0)
        val to    = toOpt.map(_.toInt).getOrElse(logic.lastUnappliedIndex)
        val found = logic.logsEntriesBetween(from, to).toList
        found.asJson
      }
    }
  }

  def getMessages(msgDir: Path) = {
    (get & path("messages") & pathEnd) {
      parameter('limit.?) { (limitOpt) =>
        complete {

          val limit = limitOpt.map(_.toInt).getOrElse(100)

          val latestMessages: Array[(NodeId, Either[ParsingFailure, Json])] =
            msgDir.children.reverse.take(limit).map(file => file.fileName -> io.circe.parser.parse(file.text))

          val found = latestMessages.map {
            case (fileName, Right(json)) => Json.obj(fileName -> json)
            case (fileName, Left(err))   => Json.obj(fileName -> Json.fromString(err.toString))
          }

          found
        }
      }
    }
  }

}
