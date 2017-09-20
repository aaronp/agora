package agora.exec.events

import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.Uri.Query

object QueryHttp extends RequestBuilding {

  def apply(queryName: String, from: String, to: String = "now") = {
    Get(Uri(s"/rest/query/$queryName").withQuery(Query("from" -> from, "to" -> to)))
  }

}
