package agora.exec.rest

import agora.api.json.JsonByteImplicits
import agora.api.time.{TimeCoords, Timestamp}
import agora.exec.events._
import agora.rest.worker.RouteSubscriptionSupport
import akka.http.scaladsl.marshalling.{ToEntityMarshaller, ToResponseMarshallable, ToResponseMarshaller}
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.{Route, StandardRoute}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto._
import io.circe.java8.time._
import io.swagger.annotations._

import scala.concurrent.Future

@Api(value = "Query", produces = "application/json")
@javax.ws.rs.Path("/")
case class QueryRoutes(monitor: SystemEventMonitor) extends RouteSubscriptionSupport
  with JsonByteImplicits
  with FailFastCirceSupport {

  def routes(includeSupportRoutes: Boolean): Route = {
    if (includeSupportRoutes) {
      queryRoutes // ~ querySystem
    } else {
      queryRoutes
    }
  }

  def queryRoutes = queryReceived //~ queryStarted ~ queryCompleted ~ queryRunning ~ queryBlocked ~ getJob

  @javax.ws.rs.Path("/rest/query/received")
  @ApiOperation(value = "Find jobs received within a time range", httpMethod = "GET", produces = "application/json")
  @ApiImplicitParams(Array(
    new ApiImplicitParam(name = "from", value = "the inclusive from date", required = true, paramType = "query"),
    new ApiImplicitParam(name = "to", value = "the inclusive to date", defaultValue = "now", required = true, paramType = "query")
  ))
  @ApiResponses(Array(
    new ApiResponse(code = 200, message = "jobs received within the time range, inclusive", response = classOf[ReceivedBetweenResponse])
  ))
  def queryReceived = {
    (get & path("rest" / "query" / "received")) {
      parameter('from, 'to.?) {
        val foo: ToEntityMarshaller[ReceivedBetweenResponse] = marshaller[ReceivedBetweenResponse]
        withRange[ReceivedBetweenResponse] {
          case (from, to) =>
            val future: Future[ReceivedBetweenResponse] = monitor.query(ReceivedBetween(from, to))
            future
        }
      }
    }
  }
//
//  @javax.ws.rs.Path("/rest/query/started")
//  @ApiOperation(value = "Find jobs started within a time range", httpMethod = "GET", produces = "application/json")
//  @ApiImplicitParams(Array(
//    new ApiImplicitParam(name = "from", value = "the inclusive from date", required = true, paramType = "query"),
//    new ApiImplicitParam(name = "to", value = "the inclusive to date", defaultValue = "now", required = true, paramType = "query")
//  ))
//  @ApiResponses(Array(
//    new ApiResponse(code = 200, message = "jobs started within the time range, inclusive", response = classOf[StartedBetweenResponse])
//  ))
//  def queryStarted = {
//    (get & path("rest" / "query" / "started")) {
//      parameter('from, 'to.?) {
//        withRange {
//          case (from, to) => monitor.query(StartedBetween(from, to))
//        }
//      }
//    }
//  }
//
//  @javax.ws.rs.Path("/rest/query/completed")
//  @ApiOperation(value = "Find jobs completed within a time range", httpMethod = "GET", produces = "application/json")
//  @ApiImplicitParams(Array(
//    new ApiImplicitParam(name = "from", value = "the inclusive from date", required = true, paramType = "query"),
//    new ApiImplicitParam(name = "to", value = "the inclusive to date", defaultValue = "now", required = true, paramType = "query")
//  ))
//  @ApiResponses(Array(
//    new ApiResponse(code = 200, message = "jobs completed within the time range, inclusive", response = classOf[CompletedBetweenResponse])
//  ))
//  def queryCompleted = {
//    (get & path("rest" / "query" / "completed")) {
//      parameter('from, 'to.?) {
//        withRange {
//          case (from, to) =>
//            val result: Future[CompletedBetweenResponse] = monitor.query(CompletedBetween(from, to))
//
//            result
//        }
//      }
//    }
//  }
//
//  @javax.ws.rs.Path("/rest/query/running")
//  @ApiOperation(value = "Find jobs which have been started but haven't completed within a time range", httpMethod = "GET", produces = "application/json")
//  @ApiImplicitParams(Array(
//    new ApiImplicitParam(name = "from", value = "the inclusive from date", required = true, paramType = "query"),
//    new ApiImplicitParam(name = "to", value = "the inclusive to date", defaultValue = "now", required = true, paramType = "query")
//  ))
//  @ApiResponses(Array(
//    new ApiResponse(code = 200, message = "jobs running within the time range, inclusive", response = classOf[NotFinishedBetweenResponse])
//  ))
//  def queryRunning = {
//    (get & path("rest" / "query" / "running")) {
//      parameter('from, 'to.?) {
//        withRange {
//          case (from, to) => monitor.query(NotFinishedBetween(from, to))
//        }
//      }
//    }
//  }
//
//  @javax.ws.rs.Path("/rest/query/blocked")
//  @ApiOperation(value = "Find jobs which have been received but have not been started within a time range, presumably due to awaiting a file dependency", httpMethod = "GET", produces = "application/json")
//  @ApiImplicitParams(Array(
//    new ApiImplicitParam(name = "from", value = "the inclusive from date", required = true, paramType = "query"),
//    new ApiImplicitParam(name = "to", value = "the inclusive to date", defaultValue = "now", required = true, paramType = "query")
//  ))
//  @ApiResponses(Array(
//    new ApiResponse(code = 200, message = "jobs blocked (received but not started due to a dependency) within the time range, inclusive", response = classOf[NotStartedBetweenResponse])
//  ))
//  def queryBlocked = {
//    (get & path("rest" / "query" / "blocked")) {
//      parameter('from, 'to.?) {
//        withRange {
//          case (from, to) => monitor.query(NotStartedBetween(from, to))
//        }
//      }
//    }
//  }
//
//  @javax.ws.rs.Path("/rest/query/system")
//  @ApiOperation(value = "Query the system startup and configuration history", httpMethod = "GET", produces = "application/json")
//  @ApiImplicitParams(Array(
//    new ApiImplicitParam(name = "from", value = "the inclusive from date", required = true, paramType = "query"),
//    new ApiImplicitParam(name = "to", value = "the inclusive to date", defaultValue = "now", required = true, paramType = "query")
//  ))
//  @ApiResponses(Array(
//    new ApiResponse(code = 200, message = "the times the REST service was started and with which configurations", response = classOf[StartTimesBetweenResponse])
//  ))
//  def querySystem = {
//    (get & path("rest" / "query" / "system")) {
//      parameter('from, 'to.?) {
//        withRange {
//          case (from, to) =>
//            val x = marshaller[StartTimesBetweenResponse]
//            monitor.query(StartTimesBetween(from, to))
//        }
//      }
//    }
//  }
//
//  @javax.ws.rs.Path("/rest/query/job/{jobID}")
//  @ApiOperation(value = "Get the received job by the given id", httpMethod = "GET", produces = "application/json")
//  @ApiImplicitParams(Array(
//    new ApiImplicitParam(name = "from", value = "the inclusive from date", required = true, paramType = "query"),
//    new ApiImplicitParam(name = "to", value = "the inclusive to date", defaultValue = "now", required = true, paramType = "query")
//  ))
//  @ApiResponses(Array(
//    new ApiResponse(code = 200, message = "The job for the given id", response = classOf[ReceivedJob]),
//    new ApiResponse(code = 404, message = "No job exists for the given id")
//  ))
//  def getJob = {
//    (get & path("rest" / "query" / "job" / Segment)) { jobId =>
//      extractExecutionContext { implicit execCtxt =>
//        complete {
//          monitor.query(FindJob(jobId)).map {
//            case FindJobResponse(Some(job)) =>
//              job
//            case FindJobResponse(None) =>
//              val x : ToResponseMarshallable = ToResponseMarshallable.apply((NotFound, s"Couldn't find job $jobId"))
//
//              x
//          }
//        }
//      }
//    }
//  }

  private def withRange[T: ToEntityMarshaller](onRange: (Timestamp, Timestamp) => Future[T]): (String, Option[String]) => StandardRoute = {
    case (TimeCoords(getFrom), opt) =>
      opt.getOrElse("now") match {
        case TimeCoords(getTo) =>
          val now = agora.api.time.now()
          val from = getFrom(now)
          val to = getTo(now)
          complete {
            onRange(from, to)
          }
        case other =>
          failWith {
            new Exception(s"Invalid 'to' query parameter: '$other'")
          }
      }
    case (from, _) =>
      failWith {
        new Exception(s"Invalid 'from' query parameter: '$from'")
      }
  }

  override def toString = s"QueryRoutes {${monitor}}"
}