package jabroni.exec.run

import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.Uri.Query
import jabroni.api.JobId
import jabroni.rest.client.RestClient

import scala.concurrent.Future

object ExecClient extends RequestBuilding {
  def asRequest(metadata: Map[String, String]) = {
    val get = Get(s"/rest/exec/search")
    Get(get.uri.withQuery(Query(metadata)))
  }

  def listMetadataRequest = Get("/rest/exec/metadata")
}

case class ExecClient(restClient: RestClient) {

  import RestClient.implicits._
  import restClient._

  def listMetadata(): Future[Map[String, List[String]]] = {
    restClient.send(ExecClient.listMetadataRequest).flatMap(_.as[Map[String, List[String]]]())
  }

  def findJobByMetadata(metadata: Map[String, String]): Future[Set[JobId]] = {
    val req = ExecClient.asRequest(metadata)
    restClient.send(req).flatMap(_.as[Set[JobId]]())
  }

}
