package miniraft.state.rest

import akka.http.scaladsl.model.StatusCodes
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto._
import jabroni.rest.BaseRoutesSpec
import jabroni.rest.test.TestTimer
import miniraft.state._

class RaftRoutesTest extends BaseRoutesSpec with FailFastCirceSupport {

  import RaftRoutesTest._

  "RaftRoutes POST /rest/raft/vote" should {
    "reply w/ a vote response" in withDir { dir =>
      val nodes = TestCluster.under(dir).of[Int]("A", "B")

      val (nodeA, aEndpoint) = nodes("A")
      val (nodeB, _) = nodes("B")

      val routes = RaftRoutes(aEndpoint).routes

      RaftHttp.apply(nodeB.mkRequestVote()) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        val response = responseAs[RequestVoteResponse]
        response.granted shouldBe true
      }
    }
  }

  "RaftRoutes POST /rest/raft/append" should {
    "reply w/ an append response" in withDir { dir =>
      val nodes = TestCluster.under(dir).of[Int]("A", "B")

      val (nodeA, aEndpoint) = nodes("A")
      val (nodeB, _) = nodes("B")

      val routes = RaftRoutes(aEndpoint).routes

      RaftHttp(nodeB.mkAppendEntries(0, Nil)) ~> routes ~> check {
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