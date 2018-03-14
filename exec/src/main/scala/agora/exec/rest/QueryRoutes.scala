package agora.exec.rest

import agora.json.AgoraJsonImplicits
import agora.time.Timestamp
import agora.exec.events.{EventQueryResponse, _}
import agora.rest.worker.RouteSubscriptionSupport
import agora.time.TimeCoords
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Route, StandardRoute}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
//import io.circe.generic.auto._
import io.swagger.annotations._

import scala.concurrent.Future

@Api(value = "Query", produces = "application/json")
@javax.ws.rs.Path("/")
case class QueryRoutes(monitor: SystemEventMonitor) extends RouteSubscriptionSupport with AgoraJsonImplicits with FailFastCirceSupport {

  def routes(includeSupportRoutes: Boolean): Route = {
    if (includeSupportRoutes) {
      queryRoutes ~ querySystem
    } else {
      queryRoutes
    }
  }

  def queryRoutes =
    queryReceived ~ queryStarted ~ queryCompleted ~ queryRunning ~ queryBlocked ~ getJob ~ firstReceived

  @javax.ws.rs.Path("/rest/query/received")
  @ApiOperation(value = "Find jobs received within a time range", httpMethod = "GET", produces = "application/json")
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(name = "from",
                           example = "10 minutes ago",
                           defaultValue = "10 minutes ago",
                           value = "the inclusive from date",
                           required = true,
                           paramType = "query"),
      new ApiImplicitParam(name = "to", value = "the inclusive to date", defaultValue = "now", required = false, paramType = "query"),
      new ApiImplicitParam(name = "filter",
                           value = "when specified, only return results which contain the command text",
                           defaultValue = "",
                           required = false,
                           paramType = "query")
    ))
  @ApiResponses(
    Array(
      new ApiResponse(code = 200, message = "jobs received within the time range, inclusive", response = classOf[ReceivedBetweenResponse])
    ))
  def queryReceived = {
    (get & path("rest" / "query" / "received")) {
      parameter('from, 'to.?, 'verbose.?, 'filter.?) {
        withRangeAndFilter[EventQueryResponse] {
          case (from, to, _, filter) => monitor.query(ReceivedBetween(from, to, filter))
        }
      }
    }
  }

  @javax.ws.rs.Path("/rest/query/started")
  @ApiOperation(value = "Find jobs started within a time range", httpMethod = "GET", produces = "application/json")
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(name = "from",
                           example = "10 minutes ago",
                           defaultValue = "10 minutes ago",
                           value = "the inclusive from date",
                           required = true,
                           paramType = "query"),
      new ApiImplicitParam(name = "to", value = "the inclusive to date", defaultValue = "now", required = false, paramType = "query"),
      new ApiImplicitParam(name = "verbose",
                           value = "whether to load the job details or only return IDs",
                           defaultValue = "false",
                           required = false,
                           paramType = "query"),
      new ApiImplicitParam(name = "filter",
                           value = "when specified, only return results which contain the command text",
                           defaultValue = "",
                           required = false,
                           paramType = "query")
    ))
  @ApiResponses(
    Array(
      new ApiResponse(code = 200, message = "jobs started within the time range, inclusive", response = classOf[StartedBetweenResponse])
    ))
  def queryStarted = {
    (get & path("rest" / "query" / "started")) {
      parameter('from, 'to.?, 'verbose.?, 'filter.?) {
        withRangeAndFilter[EventQueryResponse] {
          case (from, to, verbose, filter) => monitor.query(StartedBetween(from, to, verbose, filter))
        }
      }
    }
  }

  @javax.ws.rs.Path("/rest/query/completed")
  @ApiOperation(value = "Find jobs completed within a time range", httpMethod = "GET", produces = "application/json")
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(name = "from",
                           example = "10 minutes ago",
                           defaultValue = "10 minutes ago",
                           value = "the inclusive from date",
                           required = true,
                           paramType = "query"),
      new ApiImplicitParam(name = "to", value = "the inclusive to date", defaultValue = "now", required = false, paramType = "query"),
      new ApiImplicitParam(name = "verbose",
                           value = "whether to load the job details or only return IDs",
                           defaultValue = "false",
                           required = false,
                           paramType = "query"),
      new ApiImplicitParam(name = "filter",
                           value = "when specified, only return results which contain the command text",
                           defaultValue = "",
                           required = false,
                           paramType = "query")
    ))
  @ApiResponses(
    Array(
      new ApiResponse(code = 200, message = "jobs completed within the time range, inclusive", response = classOf[CompletedBetweenResponse])
    ))
  def queryCompleted = {
    (get & path("rest" / "query" / "completed")) {
      parameter('from, 'to.?, 'verbose.?, 'filter.?) {
        withRangeAndFilter[EventQueryResponse] {
          case (from, to, verbose, filter) => monitor.query(CompletedBetween(from, to, verbose, filter))
        }
      }
    }
  }

  @javax.ws.rs.Path("/rest/query/running")
  @ApiOperation(value = "Find jobs which have been started but haven't completed within a time range", httpMethod = "GET", produces = "application/json")
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(name = "from",
                           example = "10 minutes ago",
                           defaultValue = "10 minutes ago",
                           value = "the inclusive from date",
                           required = true,
                           paramType = "query"),
      new ApiImplicitParam(name = "to", value = "the inclusive to date", defaultValue = "now", required = false, paramType = "query"),
      new ApiImplicitParam(name = "verbose",
                           value = "whether to load the job details or only return IDs",
                           defaultValue = "false",
                           required = false,
                           paramType = "query"),
      new ApiImplicitParam(name = "filter",
                           value = "when specified, only return results which contain the command text",
                           defaultValue = "",
                           required = false,
                           paramType = "query")
    ))
  @ApiResponses(
    Array(
      new ApiResponse(code = 200, message = "jobs running within the time range, inclusive", response = classOf[NotFinishedBetweenResponse])
    ))
  def queryRunning = {
    (get & path("rest" / "query" / "running")) {
      parameter('from, 'to.?, 'verbose.?, 'filter.?) {
        withRangeAndFilter[EventQueryResponse] {
          case (from, to, verbose, filter) => monitor.query(NotFinishedBetween(from, to, verbose, filter))
        }
      }
    }
  }

  @javax.ws.rs.Path("/rest/query/blocked")
  @ApiOperation(
    value = "Find jobs which have been received but have not been started within a time range, presumably due to awaiting a file dependency",
    httpMethod = "GET",
    produces = "application/json"
  )
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(name = "from",
                           example = "10 minutes ago",
                           defaultValue = "10 minutes ago",
                           value = "the inclusive from date",
                           required = true,
                           paramType = "query"),
      new ApiImplicitParam(name = "to", value = "the inclusive to date", defaultValue = "now", required = false, paramType = "query"),
      new ApiImplicitParam(name = "filter",
                           value = "when specified, only return results which contain the command text",
                           defaultValue = "",
                           required = false,
                           paramType = "query")
    ))
  @ApiResponses(
    Array(
      new ApiResponse(
        code = 200,
        message = "jobs blocked (received but not started due to a dependency) within the time range, inclusive",
        response = classOf[NotStartedBetweenResponse]
      )
    ))
  def queryBlocked = {
    (get & path("rest" / "query" / "blocked")) {
      parameter('from, 'to.?, 'verbose.?, 'filter.?) {
        withRangeAndFilter[EventQueryResponse] {
          case (from, to, _, filter) =>
            monitor.query(NotStartedBetween(from, to, filter))
        }
      }
    }
  }

  @javax.ws.rs.Path("/rest/query/system")
  @ApiOperation(value = "Query the system startup and configuration history", httpMethod = "GET", produces = "application/json")
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(name = "from",
                           example = "10 minutes ago",
                           defaultValue = "10 minutes ago",
                           value = "the inclusive from date",
                           required = true,
                           paramType = "query"),
      new ApiImplicitParam(name = "to", value = "the inclusive to date", defaultValue = "now", required = false, paramType = "query")
    ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "the times the REST service was started and with which configurations", response = classOf[StartTimesBetweenResponse])
  ))
  def querySystem = {
    (get & path("rest" / "query" / "system")) {
      parameter('from, 'to.?, 'verbose.?, 'filter.?) {
        withRangeAndFilter[EventQueryResponse] {
          case (from, to, _, _) =>
            monitor.query(StartTimesBetween(from, to))
        }
      }
    }
  }

  @javax.ws.rs.Path("/rest/query/first")
  @ApiOperation(value = "return the first received job", httpMethod = "GET", produces = "application/json")
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(name = "event",
                           example = "received",
                           defaultValue = "received",
                           value = "one of received, started, completed",
                           required = true,
                           paramType = "query")))
  @ApiResponses(
    Array(
      new ApiResponse(code = 200, message = "the first recorded received job", response = classOf[Option[ReceivedJob]])
    ))
  def firstReceived = {
    (get & path("rest" / "query" / "first")) {
      parameter('event) { eventName =>
        complete {
          val eventOpt                           = FindFirst.values.find(_.eventName.equalsIgnoreCase(eventName))
          val event: FindFirst                   = eventOpt.getOrElse(sys.error(s"Invalid event '$eventName'. Expected one of ${FindFirst.validValues.mkString("[", ",", "]")}"))
          val future: Future[EventQueryResponse] = monitor.query(event)
          future
        }
      }
    }
  }

  @javax.ws.rs.Path("/rest/query/job/{jobID}")
  @ApiOperation(value = "Get the received job by the given id", httpMethod = "GET", produces = "application/json")
  @ApiImplicitParams(
    Array(
      new ApiImplicitParam(name = "jobID", value = "the job id", required = true, paramType = "path")
    ))
  @ApiResponses(
    Array(
      new ApiResponse(code = 200, message = "The job for the given id", response = classOf[ReceivedJob])
    ))
  def getJob = {
    (get & path("rest" / "query" / "job" / Segment)) { jobId =>
      complete {
        // we have a marshaller for the base trait EventQueryResponse, not the specific FindJobResponse sub-class,
        // hence the need to explicitly type the future here
        val eventQueryResponseFuture: Future[EventQueryResponse] = monitor.query(FindJob(jobId))
        eventQueryResponseFuture
      }
    }
  }

  private def withRangeAndFilter[T: ToEntityMarshaller](
      onRange: (Timestamp, Timestamp, Boolean, JobFilter) => Future[T]): (String, Option[String], Option[String], Option[String]) => StandardRoute = {
    case (TimeCoords(getFrom), opt, verboseOpt, filterOpt) =>
      val verbose   = verboseOpt.exists(_.toLowerCase.trim == "true")
      val jobFilter = JobFilter(filterOpt.map(_.trim).getOrElse(""))
      opt.getOrElse("now") match {
        case TimeCoords(getTo) =>
          val now  = agora.time.now()
          val from = getFrom(now)
          val to   = getTo(now)
          complete {
            onRange(from, to, verbose, jobFilter)
          }
        case other =>
          failWith {
            new Exception(s"Invalid 'to' query parameter: '$other'")
          }
      }
    case (from, _, _, _) =>
      failWith {
        new Exception(s"Invalid 'from' query parameter: '$from'")
      }
  }

  override def toString = s"QueryRoutes {${monitor}}"
}
