package agora.api.exchange

import agora.BaseSpec
import agora.api.Implicits._

import scala.language.reflectiveCalls

class MatchObserverTest extends BaseSpec {

  "MatchObserver.onceWhen" should {
    "remove the observer after one event" in {
      object obs extends MatchObserver

      val id                = agora.api.nextJobId()
      var notifiedJobs      = List[SubmitJob]()
      val jobOne: SubmitJob = 123.asJob.add("id" -> "one")

      // call the method under test 'when'
      val observer = obs.onceWhen {
        case MatchNotification(_, `jobOne`, _) => notifiedJobs = jobOne :: notifiedJobs
      }

      // this shouldn't be called, so we shouldn't be notified
      obs(MatchNotification(id, "meh".asJob, Nil))
      notifiedJobs shouldBe (empty)

      // we should be called on this one and then removed
      obs.apply(MatchNotification(id, jobOne, Nil))
      notifiedJobs should contain only (jobOne)

      // prove that calling again doesn't notify us
      obs.apply(MatchNotification(id, jobOne, Nil))
      notifiedJobs should contain only (jobOne)

      // and calling 'remove' returns false
      observer.remove() shouldBe false
    }
    "remove the observer only after remove is called" in {
      val id = agora.api.nextJobId()
      object obs extends MatchObserver

      var notifiedJobs      = List[SubmitJob]()
      val jobOne: SubmitJob = 123.asJob.add("id" -> "one")
      val jobTwo: SubmitJob = 456.asJob.add("id" -> "two")

      // call the method under test 'when'
      val observer = obs.alwaysWhen {
        case MatchNotification(_, `jobOne`, _) => notifiedJobs = jobOne :: notifiedJobs
        case MatchNotification(_, `jobTwo`, _) => notifiedJobs = jobTwo :: notifiedJobs
      }

      // we should be called on this one and then removed
      obs.apply(MatchNotification(id, jobOne, Nil))
      obs.apply(MatchNotification(id, jobTwo, Nil))
      obs.apply(MatchNotification(id, jobOne, Nil))
      notifiedJobs.size shouldBe 3
      notifiedJobs shouldBe List(jobOne, jobTwo, jobOne)

      // and calling 'remove' returns false
      observer.remove() shouldBe true
      observer.remove() shouldBe false

      // call again to check we don't get notified
      obs.apply(MatchNotification(id, jobOne, Nil))
      notifiedJobs shouldBe List(jobOne, jobTwo, jobOne)

    }
  }
}
