package agora.api.exchange

import java.util.concurrent.atomic.AtomicInteger

import agora.BaseSpec
import agora.api.Implicits._
import agora.api.worker.HostLocation
import io.circe.generic.auto._

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.Future

class SubmitableTest extends BaseSpec {

  import SubmitableTest._

  "Submitable.withDetails" should {
    "be able to specify an implicit submitable based on another's low-priority submitable" in {

      val foo: Submitable[Add] = Submitable.instance[Add].withDetails(_.matchingPath("foo"))

      foo
        .asSubmitJob(Add(1, 2))
        .submissionDetails
        .workMatcher shouldBe ("path" === "foo").asMatcher
    }
  }
  "Submitable" should {
    "use the work subscription in scope" in {
      implicit val details: SubmissionDetails = SubmissionDetails().matchingPath("/rest/foo")
      // enqueues the add request. The asClient is picked up from the Add's companion object
      val job = Submitable.instance[Add].asSubmitJob(Add(1, 2))
      job.submissionDetails shouldBe details
    }
  }

  "Submitable.enqueueTo" should {
    "use the work subscription in scope" in {
      val subscription1                       = WorkSubscription.localhost(1234).withPath("/rest/foo")
      val subscription2                       = subscription1.withDetails(_.withLocation(HostLocation.localhost(2345)))
      implicit val details: SubmissionDetails = SubmissionDetails().matchingPath("/rest/foo")

      val exchange = ServerSideExchange()

      val sub1 = exchange.subscribe(subscription1).futureValue
      val sub2 = exchange.subscribe(subscription2).futureValue
      exchange.request(sub1.id, 1)
      exchange.request(sub2.id, 1)

      // enqueues the add request. The asClient is picked up from the Add's companion object
      val three: Int = exchange.enqueue(Add(1, 2)).futureValue
      val seven: Int = exchange.enqueue(Add(3, 4)).futureValue

      SubmitableTest.Add.AddAsClient.callsByLocation
        .mapValues(_.get())
        .toList should contain only (HostLocation.localhost(1234) -> 1, HostLocation.localhost(2345) -> 1)

      val queue = exchange.queueState().futureValue
      queue.jobs shouldBe empty
      queue.subscriptions.size shouldBe 2
      queue.subscriptions.foreach(_.requested shouldBe 0)
    }
  }

}

object SubmitableTest {

  case class Add(x: Int, y: Int)

  object Add {

    implicit object AddAsClient extends AsClient[Add, Int] {
      val callsByLocation = mutable.HashMap[HostLocation, AtomicInteger]()

      override def dispatch[T <: Add](dispatch: Dispatch[T]): Future[Int] = {
        callsByLocation
          .getOrElseUpdate(dispatch.matchedWorker.location, new AtomicInteger(0))
          .incrementAndGet()
        val add = dispatch.request
        Future.successful(new Calculator(dispatch.matchedWorker.location).add(add))
      }
    }

  }

  implicit def asAdd: AsClient[Add, Int] = Add.AddAsClient

  class Calculator(val hostLocation: HostLocation) {
    def add(request: Add): Int = request.x + request.y
  }

}
