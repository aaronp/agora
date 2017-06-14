package jabroni.exec.run

import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import jabroni.api.JobId
import jabroni.rest.client.RestClient

import scala.concurrent.Future

object ExecClient extends RequestBuilding {
  def asRequest(metadata: Map[String, String]) = {

    val headers = metadata.map {
      case (key, value) =>
        HttpHeader.parse(key, value) match {
          case ParsingResult.Ok(h, _) => h
          case result => sys.error(s"Invalid header for $key: '${value}' : ${result.errors.mkString(",")}")
        }

    }
    Get("/rest/exec/search").withHeaders(headers.toList)
  }

  def listMetadataRequest = Get("/rest/exec/metadata")
}

case class ExecClient(restClient: RestClient) {

  import RestClient.implicits._

  def listMetadata(): Future[Map[String, List[String]]] = {
    restClient.send(ExecClient.listMetadataRequest).flatMap(_.as[Map[String, List[String]]]())
  }

  def findJobByMetadata(metadata: Map[String, String]): Future[Set[JobId]] = {
    val req = ExecClient.asRequest(metadata)
    restClient.send(req).flatMap(_.as[Set[JobId]]())
  }

}
