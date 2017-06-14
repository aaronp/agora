package miniraft.state.rest

import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.{ContentTypes, HttpEntity}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Encoder
import io.circe.generic.auto._
import io.circe.syntax._
import miniraft.{AppendEntries, RequestVote}

import scala.concurrent.ExecutionContext

object RaftHttp extends RequestBuilding with FailFastCirceSupport {

  //  val tem = implicitly[ToEntityMarshaller[Json]]

  def apply(vote: RequestVote)(implicit ec: ExecutionContext) = {
    Post("/rest/raft/vote", vote.asJson)
  }

  def apply[T: Encoder](append: AppendEntries[T])(implicit ec: ExecutionContext) = {
    Post("/rest/raft/append", append.asJson)
  }

  object leader {

    def append[T: Encoder](value: T) = {
      val json = value.asJson
      val e    = HttpEntity(ContentTypes.`application/json`, json.noSpaces)
      Post("/rest/raft/leader/append").withEntity(e)
    }
  }

  object support {
    val state = Get("/rest/raft/support/state")

    def logs(from: Option[Int], to: Option[Int]) = {
      val queryString = asQueryString(from.map("from=" + _).toList ++ to.map("to=" + _))
      Get("/rest/raft/support/logs" + queryString)
    }

    def recentMessages(limit: Option[Int]) = {
      val params = limit.toList.map("limit=" + _)
      Get("/rest/raft/support/messages" + asQueryString(params))
    }

    val forceElectionTimeout = Get("/rest/raft/support/state")

    private def asQueryString(params: List[String]) = params match {
      case Nil  => ""
      case list => list.mkString("?", "&", "")
    }
  }

}
