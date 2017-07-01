package miniraft.state.rest

import akka.http.scaladsl.model.StatusCodes
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto._
import agora.rest.BaseRoutesSpec
import agora.rest.test.TestTimer
import akka.http.scaladsl.server.Route
import miniraft.state.RaftNode.async
import miniraft.{AppendEntries, AppendEntriesResponse, RequestVote, RequestVoteResponse}
import miniraft.state._

class RaftRoutesTest extends BaseRoutesSpec with FailFastCirceSupport {

  import RaftRoutesTest._

  "RaftRoutes POST /rest/raft/vote" should {
    "reply w/ a vote response" in withDir { dir =>
      val nodes = TestCluster.under(dir).of[Int]("A", "B") {
        case _ => ???
      }

      val nodeA                                      = nodes("A")
      val nodeB: async.RaftNodeActorClient[LogIndex] = nodes("B")

      val routes: Route = RaftRoutes(nodeA).routes

      RaftHttp(RequestVote(Term(2), "A", 0, Term(0))) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        val response = responseAs[RequestVoteResponse]
        response.granted shouldBe true
      }
    }
  }

  "RaftRoutes POST /rest/raft/append" should {
    "reply w/ an append response" in withDir { dir =>
      val nodes = TestCluster.under(dir).of[Int]("A", "B") {
        case _ => ???
      }

      val nodeA = nodes("A")
      val nodeB = nodes("B")

      val routes = RaftRoutes(nodeA).routes

      val ae = AppendEntries[Int](Term(2), "A", 0, 0, Term(2), None)
      RaftHttp(ae) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        val response = responseAs[AppendEntriesResponse]
        response.matchIndex shouldBe 0
        response.success shouldBe true
      }
    }
  }
}

object RaftRoutesTest {

  case class SomeCommand(foo: Int, bar: String)

}
