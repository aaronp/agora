package agora.exec.rest

import agora.api.json.JsonByteImplicits
import agora.api.time.{Timestamp, now}
import agora.exec.events._
import agora.exec.model.RunProcess
import agora.rest.BaseRoutesSpec
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto._
import io.circe.java8.time._
import org.scalatest.concurrent.Eventually

import scala.util.Success

class QueryRoutesTest extends BaseRoutesSpec with Eventually with FailFastCirceSupport with JsonByteImplicits {

  implicit def asRichSystemEventMonitor(queryRoutes: QueryRoutes) = new {
    def insertJob(id: String, time: Timestamp): ReceivedJob = {
      val job = ReceivedJob(id, None, RunProcess(id), time)
      queryRoutes.monitor.accept(job)
      eventually {
        queryRoutes.monitor.query(FindJob(id)).futureValue.job.map(_.id).toList should contain(id)
      }
      job
    }

    def startJob(id: String, time: Timestamp): StartedJob = {
      val job = StartedJob(id, time)
      queryRoutes.monitor.accept(job)
      eventually {
        queryRoutes.monitor.query(StartedBetween(time, time)).futureValue.started should contain(job)
      }
      job
    }

    def completeJob(id: String, time: Timestamp): CompletedJob = {
      val job = CompletedJob(id, Success(0), time)
      queryRoutes.monitor.accept(job)
      eventually {
        queryRoutes.monitor.query(CompletedBetween(time, time)).futureValue.completed should contain(job)
      }
      job
    }
  }

  "GET /rest/query/received?from=10 minutes ago&to=2 minutes ago" should {
    "retrieve jobs received within the time range" in {
      withRoute { queryRoutes =>
        val jobA = queryRoutes.insertJob("a", now().minusMinutes(2))
        val jobB = queryRoutes.insertJob("b", now().minusMinutes(1))

        QueryHttp("received", "10 minutes ago", "3 minutes ago") ~> queryRoutes.queryRoutes ~> check {
          val received: ReceivedBetweenResponse = responseAs[ReceivedBetweenResponse]
          received.received shouldBe empty
        }

        QueryHttp("received", "90 seconds ago") ~> queryRoutes.queryRoutes ~> check {
          responseAs[ReceivedBetweenResponse].received should contain only (jobB)
        }

        QueryHttp("received", "3 minutes ago", "90 seconds ago") ~> queryRoutes.queryRoutes ~> check {
          responseAs[ReceivedBetweenResponse].received should contain only (jobA)
        }
      }
    }
  }
  "GET /rest/query/started?from=10 minutes ago" should {
    "retrieve jobs started within the time range" in {
      withRoute { queryRoutes =>
        val started = queryRoutes.startJob("a", now().minusMinutes(1))

        QueryHttp("started", "2 minutes ago") ~> queryRoutes.queryRoutes ~> check {
          responseAs[StartedBetweenResponse].started should contain(started)
        }
      }
    }
  }
  "GET /rest/query/completed?from=10 minutes ago" should {
    "retrieve jobs completed within the time range" in {
      withRoute { queryRoutes =>
        val job = queryRoutes.completeJob("a", now().minusMinutes(1))

        QueryHttp("completed", "2 minutes ago") ~> queryRoutes.queryRoutes ~> check {
          responseAs[CompletedBetweenResponse].completed should contain(job)
        }
      }
    }
  }

  def withRoute[T](f: QueryRoutes => T): T = {
    withDir { dir =>
      val monitor: SystemEventMonitor = SystemEventMonitor(dir)(system)
      f(QueryRoutes(monitor))
    }
  }
}
