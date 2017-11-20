package miniraft.state

import agora.BaseSpec
import agora.api.worker.HostLocation
import agora.rest.RunningService
import miniraft.state.rest.{LeaderClient, NodeStateSummary}
import org.scalatest.concurrent.Eventually

import scala.concurrent.{Await, Future}

object RaftSystemTest {

  case class RunningNode(service: RunningService[RaftConfig, RaftSystem[String]]) {
    val leader: LeaderClient[String] = service.conf.leaderClient[String]
    val support                      = service.conf.supportClient[String]

    service.onShutdown {
      import concurrent.duration._
      val fut1 = service.conf.clientConfig.cachedClients.stop()
      val fut2 = service.conf.serverImplicits.stop()
      Await.result(fut1, 4.seconds)
      Await.result(fut2, 4.seconds)
    }

    def about: Future[String] = {

      import io.circe.syntax._
      import support.client.executionContext

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
      val server: RunningService[RaftConfig, RaftSystem[String]] = rs.start().futureValue
      RunningNode(server)
    }

  }
}
