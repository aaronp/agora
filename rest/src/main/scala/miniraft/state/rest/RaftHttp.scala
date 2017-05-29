package miniraft.state.rest

import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.{Encoder, Json}
import miniraft.state.{AppendEntries, RequestVote}

import scala.concurrent.ExecutionContext

object RaftHttp extends RequestBuilding with FailFastCirceSupport {

  val tem = implicitly[ToEntityMarshaller[Json]]

  def apply(vote: RequestVote)(implicit ec: ExecutionContext) = {
    Post("/rest/raft/vote", vote.asJson)
  }

  def apply[T: Encoder](append: AppendEntries[T])(implicit ec: ExecutionContext) = {
    Post("/rest/raft/append", append.asJson)
  }

}
