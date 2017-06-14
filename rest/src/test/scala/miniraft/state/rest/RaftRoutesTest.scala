package miniraft.state.rest

import akka.http.scaladsl.model.StatusCodes
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto._
import agora.rest.BaseRoutesSpec
import agora.rest.test.TestTimer
import miniraft.{AppendEntriesResponse, RequestVoteResponse}
import miniraft.state._

class RaftRoutesTest extends BaseRoutesSpec with FailFastCirceSupport {

  import RaftRoutesTest._

  "RaftRoutes POST /rest/raft/vote" should {
    "reply w/ a vote response" in withDir { dir =>
      val nodes = TestCluster.under(dir).of[Int]("A", "B")(_ => ???)

      val nodeA = nodes("A")
      val nodeB = nodes("B")

      val routes = RaftRoutes(nodeA.endpoint).routes

      RaftHttp.apply(nodeB.logic.mkRequestVote()) ~> routes ~> check {
        status shouldEqual StatusCodes.OK
        val response = responseAs[RequestVoteResponse]
        response.granted shouldBe true
      }
    }
  }

  "RaftRoutes POST /rest/raft/append" should {
    "reply w/ an append response" in withDir { dir =>
      val nodes = TestCluster.under(dir).of[Int]("A", "B")(_ => ???)

      val nodeA = nodes("A")
      val nodeB = nodes("B")

      val routes = RaftRoutes(nodeA.endpoint).routes

      RaftHttp(nodeB.logic.mkHeartbeatAppendEntries(0)) ~> routes ~> check {
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
