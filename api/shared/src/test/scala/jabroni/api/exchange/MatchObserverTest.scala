package jabroni.api.exchange

import io.circe.Encoder
import jabroni.api.Implicits._
import org.scalatest.{Matchers, WordSpec}

import scala.language.reflectiveCalls

class MatchObserverTest extends WordSpec with Matchers {

  "MatchObserver.onceWhen" should {
    "remove the observer after one event" in {
      object obs extends MatchObserver

      val strEnc: Encoder[String] = implicitly[Encoder[String]]


      var notifiedJobs = List[SubmitJob]()
      val jobOne: SubmitJob = 123.asJob() + ("id" -> "one")

      // call the method under test 'when'
      val observer = obs.onceWhen {
        case (`jobOne`, _) => notifiedJobs = jobOne :: notifiedJobs
      }

      // this shouldn't be called, so we shouldn't be notified
      obs("meh".asJob, Nil)
      notifiedJobs shouldBe (empty)

      // we should be called on this one and then removed
      obs.apply(jobOne -> Nil)
      notifiedJobs should contain only (jobOne)


      // prove that calling again doesn't notify us
      obs.apply(jobOne -> Nil)
      notifiedJobs should contain only (jobOne)

      // and calling 'remove' returns false
      observer.remove() shouldBe false
    }
    "remove the observer only after remove is called" in {
      object obs extends MatchObserver

      var notifiedJobs = List[SubmitJob]()
      val jobOne: SubmitJob = 123.asJob() + ("id" -> "one")
      val jobTwo: SubmitJob = 456.asJob() + ("id" -> "two")

      // call the method under test 'when'
      val observer = obs.alwaysWhen {
        case (`jobOne`, _) => notifiedJobs = jobOne :: notifiedJobs
        case (`jobTwo`, _) => notifiedJobs = jobTwo :: notifiedJobs
      }


      // we should be called on this one and then removed
      obs.apply(jobOne -> Nil)
      obs.apply(jobTwo -> Nil)
      obs.apply(jobOne -> Nil)
      notifiedJobs.size shouldBe 3
      notifiedJobs shouldBe List(jobOne, jobTwo, jobOne)

      // and calling 'remove' returns false
      observer.remove() shouldBe true
      observer.remove() shouldBe false

      // call again to check we don't get notified
      obs.apply(jobOne -> Nil)
      notifiedJobs shouldBe List(jobOne, jobTwo, jobOne)

    }
  }
}
