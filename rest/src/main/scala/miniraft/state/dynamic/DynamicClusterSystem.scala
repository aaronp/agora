package miniraft.state

import java.nio.file.Path

import agora.api.io.implicits._
import agora.api.worker.HostLocation
import agora.rest.RunningService
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto._
import miniraft.LeaderApi

import scala.concurrent.ExecutionContext
import scala.language.reflectiveCalls

/**
  * Provides a Raft setup which can add/remote cluster nodes at runtime
  *
  */
object DynamicClusterSystem extends FailFastCirceSupport {


  def main(args: Array[String]): Unit = {
    val config: RaftConfig = RaftConfig(args)
    val raftSystem: RaftSystem[DynamicClusterMessage] = apply(config)

    import config.serverImplicits._
    raftSystem.start(routes(raftSystem)).foreach(run)
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


  def run(service: RunningService[RaftConfig, RaftNode[DynamicClusterMessage] with LeaderApi[DynamicClusterMessage]]) = {
    val location = service.conf.location
    service.service.append(AddHost(location))
  }

  def apply(config: RaftConfig): RaftSystem[DynamicClusterMessage] = {

    val clusterNodesFile: Path = config.persistentDir.resolve("cluster.nodes").createIfNotExists()
    val svc = DynamicHostService(config, clusterNodesFile)

    val raftSystem: RaftSystem[DynamicClusterMessage] = RaftSystem[DynamicClusterMessage](config, svc.clusterNodes()) {
      (entry: LogEntry[DynamicClusterMessage]) =>
        svc.onDynamicClusterMessage(entry.command)
    }
    svc.raftSystem = raftSystem
    raftSystem
  }

}
