package miniraft.state

import java.nio.file.Path

import agora.api.config.HostLocation
import agora.io.implicits._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto._
import miniraft.LeaderApi

import scala.concurrent.{ExecutionContext, Future}
import scala.language.reflectiveCalls

/**
  * Provides a Raft setup which can add/remote cluster nodes at runtime
  *
  */
object DynamicClusterSystem extends FailFastCirceSupport with StrictLogging {

  def main(args: Array[String]): Unit = {
    val config: RaftConfig = RaftConfig(args)
    import config.serverImplicits._
    val future = for {
      raftSystem <- apply(config)
      running    <- raftSystem.start(routes(raftSystem))
    } yield {
      running
    }

    future.onComplete { done =>
      logger.info(s"Dynamic cluster completed w/ : $done")
    }
  }

  def routes(raftSystem: RaftSystem[DynamicClusterMessage]) = {
    raftSystem.routes() ~ removeNode(raftSystem.leader)
  }

  def removeNode(leader: LeaderApi[DynamicClusterMessage]): Route = {
    def remove(location: HostLocation)(implicit ec: ExecutionContext) = {
      leader.append(RemoveHost(location)).flatMap(_.result)
    }

    path("rest" / "raft" / "remove") {
      extractExecutionContext { implicit ec =>
        delete {
          entity(as[HostLocation]) { location =>
            complete {
              remove(location)
            }
          }
        } ~ get {
          (parameter('host, 'port)) { (host, port) =>
            complete {
              remove(HostLocation(host, port.toInt))
            }
          }
        }
      }
    }
  }

  def apply(config: RaftConfig): Future[RaftSystem[DynamicClusterMessage]] = {

    val clusterNodesFile: Path = config.logDir.resolve("cluster.nodes").createIfNotExists()
    val svc                    = DynamicHostService(config, clusterNodesFile)

    val raftSystem: RaftSystem[DynamicClusterMessage] = RaftSystem[DynamicClusterMessage](config, svc.clusterNodes()) {
      (entry: LogEntry[DynamicClusterMessage]) =>
        svc.onDynamicClusterMessage(entry.command)
    }

    import config.serverImplicits._
    val canJoinFuture: Future[Boolean] = svc.raftSystem = raftSystem

    import akka.http.scaladsl.util.FastFuture
    import FastFuture._
    canJoinFuture.fast.flatMap {
      case true =>
        //
        //
        FastFuture.successful(raftSystem)
      case false => FastFuture.failed(new Exception("Couldn't join the cluster"))
    }

  }

}
