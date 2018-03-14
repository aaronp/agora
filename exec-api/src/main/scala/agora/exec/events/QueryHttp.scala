package agora.exec.events

import agora.api.JobId
import agora.time.TimeCoords.FixedDateTime.format
import agora.time.Timestamp
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.{HttpRequest, Uri}

object QueryHttp extends RequestBuilding {

  def apply(queryName: String, from: String, to: String = "now") = {
    Get(Uri(s"/rest/query/$queryName").withQuery(Query("from" -> from, "to" -> to)))
  }

  def apply(jobId: JobId): HttpRequest = Get(Uri(s"/rest/query/job/$jobId"))

  def apply(query: FindFirst): HttpRequest = Get(Uri("/rest/query/first").withQuery(Query("event" -> query.eventName)))

  def apply(query: ReceivedBetween): HttpRequest = {
    Get(Uri("/rest/query/received").withQuery(mkQuery(query.from, query.to, query.filter)))
  }

  def apply(query: StartedBetween): HttpRequest = {
    Get(Uri("/rest/query/started").withQuery(mkQuery(query.from, query.to, query.filter, query.verbose)))
  }

  def apply(query: CompletedBetween): HttpRequest = {
    Get(Uri("/rest/query/completed").withQuery(mkQuery(query.from, query.to, query.filter, query.verbose)))
  }

  def apply(query: NotFinishedBetween): HttpRequest = {
    Get(Uri("/rest/query/running").withQuery(mkQuery(query.from, query.to, query.filter, query.verbose)))
  }

  def apply(query: NotStartedBetween): HttpRequest = {
    Get(Uri("/rest/query/blocked").withQuery(mkQuery(query.from, query.to, query.filter)))
  }

  def apply(query: StartTimesBetween): HttpRequest = {
    Get(Uri("/rest/query/system").withQuery(Query("from" -> format(query.from), "to" -> format(query.to))))
  }

  def mkQuery(from: Timestamp, to: Timestamp, filter: JobFilter, verbose: Boolean = false) = {
    val baseParams = List("from" -> format(from), "to" -> format(to), "verbose" -> verbose.toString)
    val params = if (filter.isEmpty) {
      baseParams
    } else {
      ("filter" -> filter.contains) +: baseParams
    }
    Query(params: _*)
  }

  def forQuery(request: EventQuery): HttpRequest = {
    request match {
      case q: FindFirst          => apply(q)
      case q: ReceivedBetween    => apply(q)
      case q: StartedBetween     => apply(q)
      case q: CompletedBetween   => apply(q)
      case q: NotFinishedBetween => apply(q)
      case q: NotStartedBetween  => apply(q)
      case q: StartTimesBetween  => apply(q)
      case q: FindJob            => apply(q.id)
    }
  }

}
