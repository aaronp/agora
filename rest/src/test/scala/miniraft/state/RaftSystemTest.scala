package miniraft.state

import agora.BaseSpec
import agora.api.worker.HostLocation
import agora.rest.RunningService
import miniraft.LeaderApi
import miniraft.state.rest.{LeaderClient, NodeStateSummary}
import org.scalatest.concurrent.Eventually

import scala.collection.immutable
import scala.concurrent.{ExecutionContext, Future}

object RaftSystemTest {

  case class RunningNode(service: RunningService[RaftConfig, RaftSystem[String]]) {
    val leader: LeaderClient[String] = service.conf.leaderClient[String]
    val support                      = service.conf.supportClient[String]

    service.onShutdown {
      service.conf.clientConfig.cachedClients.close()
      service.conf.serverImplicits.close()
    }

    def about: Future[String] = {

      import support.client.executionContext
      import io.circe.syntax._

      for {
        summary <- support.state()
        msgs    <- support.recentMessages(Option(10))
      } yield {
        val recent = msgs.zipWithIndex.map {
          case (msg, i) =>
            s"""MSG $i
               |${msg.spaces4}
             """.stripMargin
        }

        s"""
           |vvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvvv
           |${summary.asJson.spaces4}
           |
           |RECENT MESSAGES:
           |${recent.mkString("\n")}
           |^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
         """.stripMargin
      }
    }
  }
}

class RaftSystemTest extends BaseSpec with Eventually {

  import RaftSystemTest._

  "RaftSystem.start" should {
    "start a cluster node" in {
      val clusterNodes: List[RunningNode]   = startLocalNodes((9000 to 9005).toSet)
      val expectedCusterState: List[String] = clusterNodes.map(_ => "follower").init :+ "leader"

      def desc: String = clusterNodes.map(_.about.futureValue).mkString("\n")

      try {
        eventually {
          val state: List[NodeStateSummary] = clusterNodes.map(_.support.state().futureValue)
          val roles                         = state.map(_.summary.role)

          withClue(desc) {
            roles.sorted shouldBe expectedCusterState
          }
        }
      } finally {
        clusterNodes.foreach(_.service.stop())
      }
    }
  }

  def startLocalNodes(ports: Set[Int]) = {

    val nodes = ports.map { port =>
      HostLocation("localhost", port)
    }

    nodes.toList.map { location =>
      val otherNodes = nodes.filterNot(_ == location)
      val config     = RaftConfig().withLocation(location).withNodes(otherNodes)
      config.seedNodeLocations.toSet shouldBe otherNodes

      val rs = RaftSystem[String](config) { entry =>
        // println(s"$location adding $entry")
      }

      import config.serverImplicits.executionContext
      val server: RunningService[RaftConfig, RaftSystem[String]] = rs.start().futureValue
      RunningNode(server)
    }

  }
}
