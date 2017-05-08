package miniraft

import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpRequest}
import io.circe.{Decoder, Encoder}

object RaftHttp extends RequestBuilding {

  import io.circe.generic.auto._
  import io.circe.syntax._

  def forVote(vote: RequestVote): HttpRequest = {
    val json = vote.asJson
    val e = HttpEntity(ContentTypes.`application/json`, json.noSpaces)
    Post(s"/rest/raft/vote").withEntity(e)
  }

  def forAppend[T : Encoder : Decoder](append: AppendEntries[T]): HttpRequest = {
    val json = append.asJson
    val e = HttpEntity(ContentTypes.`application/json`, json.noSpaces)
    Post(s"/rest/raft/append").withEntity(e)
  }

}
