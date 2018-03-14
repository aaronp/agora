package agora.api.exchange.observer

import agora.BaseApiSpec
import agora.api.exchange.SubmitJob
import agora.api.Implicits._

class ExchangeObserverDelegateTest extends BaseApiSpec {

  val someTime = agora.time.now()

  "ExchangeObserverDelegate.onceWhen" should {
    "remove the observer after one event" in {
      object obs extends ExchangeObserverDelegate

      val id                = agora.api.nextJobId()
      var notifiedJobs      = List[SubmitJob]()
      val jobOne: SubmitJob = 123.asJob.add("id" -> "one")

      // call the method under test 'when'
      val observer = obs.onceWhen {
        case OnMatch(_, _, `jobOne`, _) => notifiedJobs = jobOne :: notifiedJobs
      }

      // this shouldn't be called, so we shouldn't be notified
      obs.onMatch(OnMatch(someTime, id, "meh".asJob, Nil))
      notifiedJobs shouldBe (empty)

      // we should be called on this one and then removed
      obs.onMatch(OnMatch(someTime, id, jobOne, Nil))
      notifiedJobs should contain only (jobOne)

      // prove that calling again doesn't notify us
      obs.onMatch(OnMatch(someTime, id, jobOne, Nil))
      notifiedJobs should contain only (jobOne)

      // and calling 'remove' returns false
      observer.remove() shouldBe false
    }
    "remove the observer only after remove is called" in {
      val id = agora.api.nextJobId()
      object obs extends ExchangeObserverDelegate

      var notifiedJobs      = List[SubmitJob]()
      val jobOne: SubmitJob = 123.asJob.add("id" -> "one")
      val jobTwo: SubmitJob = 456.asJob.add("id" -> "two")

      // call the method under test 'when'
      val observer = obs.alwaysWhen {
        case OnMatch(_, _, `jobOne`, _) => notifiedJobs = jobOne :: notifiedJobs
        case OnMatch(_, _, `jobTwo`, _) => notifiedJobs = jobTwo :: notifiedJobs
      }

      // we should be called on this one and then removed
      obs.onMatch(OnMatch(someTime, id, jobOne, Nil))
      obs.onMatch(OnMatch(someTime, id, jobTwo, Nil))
      obs.onMatch(OnMatch(someTime, id, jobOne, Nil))
      notifiedJobs.size shouldBe 3
      notifiedJobs shouldBe List(jobOne, jobTwo, jobOne)

      // and calling 'remove' returns false
      observer.remove() shouldBe true
      observer.remove() shouldBe false

      // call again to check we don't get notified
      obs.onMatch(OnMatch(someTime, id, jobOne, Nil))
      notifiedJobs shouldBe List(jobOne, jobTwo, jobOne)

    }
  }
}
