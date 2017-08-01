package agora.api.exchange

import agora.api.Implicits._
import agora.api.worker.SubscriptionKey
import org.scalatest.{Matchers, WordSpec}

class ExchangeStateTest extends WordSpec with Matchers {

  "ExchangeState.cancel" ignore {
    "cancel known subscriptions" in {
      ???
    }
    "cancel composite subscriptions which contain the cancelled subscription" in {
      ???

    }
  }

  "ExchangeState.request" ignore {
    "request additional entries from all consistuent suscriptions when a composite subscription is specified" in {
      ???
    }
  }
  "ExchangeState.matches" should {
    "find which jobs match which subscriptions" in new TestData {
      // call the method under test
      val (matches, newState) = state.matches

      // both work subscriptions should match and thus be decremented
      newState.subscriptionsById shouldBe Map[SubscriptionKey, (WorkSubscription, Int)]("s1" -> (s1, 0), "s2" -> (s2, 1), "never match sub" -> (neverMatchSubscription, 10))

      newState.jobsById shouldBe Map("never match job" -> neverMatchJob)
      matches should contain only (
        MatchNotification("j1", j1, List(Candidate("s1", s1, 0))),
        MatchNotification("j2", j2, List(Candidate("s2", s2, 1)))
      )
    }

    "not match subscriptions with no available slots" in new TestData {
      val empty = Map[SubscriptionKey, (WorkSubscription, Int)]("just has the one" -> (s1, 1), "exhausted" -> (s2, 0))

      // call the method under test
      val (matches, newState) = state.copy(subscriptionsById = empty).matches
      val newSubscriptions    = newState.subscriptionsById

      // only 'just has the one' should match
      newSubscriptions shouldBe Map[SubscriptionKey, (WorkSubscription, Int)]("just has the one" -> (s1, 0), "exhausted"  -> (s2, 0))
      newState.jobsById shouldBe Map("j2"                                                        -> j2, "never match job" -> neverMatchJob, "never match job" -> neverMatchJob)
      matches should contain only (
        MatchNotification("j1",
                          j1,
                          List(Candidate("just has the one", s1, 0)))
      )
    }

    "match composite subscriptions if the constituent subscriptions match" in new TestData {

      val composite      = Map[SubscriptionKey, Compose]("composite" -> Compose(compositeSubscription, "s1", "s2"))
      val compositeState = state.copy(compositeSubscriptionsById = composite)

      // call the method under test
      val (matches, newState) = compositeState.matches(MatchAll)
      val newSubscriptions    = newState.subscriptionsById

      // 's1' should be decremented from 1 to 0, and 's2' from '2' to '1'.
      // job 'j2' should then match 's2'
      newSubscriptions shouldBe Map[SubscriptionKey, (WorkSubscription, Int)]("s1" -> (s1, 0), "s2" -> (s2, 0), "never match sub" -> (neverMatchSubscription, 10))

      newState.jobsById shouldBe Map("never match job" -> neverMatchJob)

      matches should contain only (
        MatchNotification("j1", j1, List(Candidate("composite", compositeSubscription, 0))),
        MatchNotification("j2", j2, List(Candidate("s2", s2, 0)))
      )
    }

    "not match composite subscriptions if any of the constituent subscriptions don't match" in new TestData {

      val composite           = Map[SubscriptionKey, Compose]("composite" -> Compose(compositeSubscription, "s1", "never match sub"))
      val compositeState      = state.copy(compositeSubscriptionsById = composite)
      val (matches, newState) = compositeState.matches
      newState.jobsById.keySet should contain("never match job")
      newState.subscriptionsById.size shouldBe 3
      matches.map(_.id) should not contain ("never match job")
      matches.flatMap(_.chosen).map(_.subscriptionKey) should not contain ("never match sub")
    }
    "not match composite subscriptions if they aren't requesting work items" in new TestData {

      val missingSubscriptions = Map("s1" -> (s1, 1), "s2" -> (s2, 0))

      val stateOnlyMatchingJ1 =
        new ExchangeState(subscriptionsById = missingSubscriptions, compositeSubscriptionsById = Map("composite" -> Compose(compositeSubscription, "s1", "s2")), jobsById = jobs)

      val (matches, newState) = stateOnlyMatchingJ1.matches

      newState.jobsById.keySet shouldBe Set("j2", "never match job")

      matches should contain only (
        MatchNotification("j1",
                          j1,
                          List(Candidate("s1", s1, 0)))
      )
    }
  }
  "ExchangeState.flattenSubscriptions" should {
    "error if a circular reference is discovered from A -> B -> C -> A" in {
      val input = Map(
        "A" -> Compose(WorkSubscription(), "B"),
        "B" -> Compose(WorkSubscription(), "C"),
        "C" -> Compose(WorkSubscription(), "A")
      )

      Set("A", "B", "C").foreach { key =>
        val exp = intercept[Exception] {
          ExchangeState.flattenSubscriptions(input, key)
        }
        println(exp)
      }

    }
    "resolve composite references" in {
      val input = Map(
        "A" -> Compose(WorkSubscription(), "B"),
        "B" -> Compose(WorkSubscription(), "C"),
        "C" -> Compose(WorkSubscription(), "D", "E")
      )
      ExchangeState.flattenSubscriptions(input, "A") shouldBe Set("D", "E")
      ExchangeState.flattenSubscriptions(input, "B") shouldBe Set("D", "E")
      ExchangeState.flattenSubscriptions(input, "C") shouldBe Set("D", "E")
    }
  }

  /**
    * Some test data -- The implicit matcher in scopt is the 'TestMatcher', which
    * matches job 'j1' with subscription 's1' and job 'j2' w/ subscription 's2'
    */
  trait TestData {

    val j1                     = "one".asJob
    val j2                     = "two".asJob
    val neverMatchJob          = "i never match anybody".asJob
    val s1                     = WorkSubscription().append("name", "s1")
    val s2                     = WorkSubscription().append("name", "s2")
    val neverMatchSubscription = WorkSubscription().append("name", "i won't match any jobs")
    val compositeSubscription  = WorkSubscription().append("name", "composite")

    object MatchAll extends JobPredicate {
      override def matches(offer: SubmitJob, work: WorkSubscription): Boolean = {
        offer != neverMatchJob && work != "never match sub"
      }
    }

    implicit object TestMatcher extends JobPredicate {
      override def matches(offer: SubmitJob, work: WorkSubscription): Boolean = {
        if (offer.eq(j1)) work.eq(s1)
        else if (offer.eq(j2)) work.eq(s2)
        else false
      }
    }

    val jobs          = Map("j1" -> j1, "j2"      -> j2, "never match job"      -> neverMatchJob)
    val subscriptions = Map("s1" -> (s1, 1), "s2" -> (s2, 2), "never match sub" -> (neverMatchSubscription, 10))

    val state = new ExchangeState(subscriptionsById = subscriptions, compositeSubscriptionsById = Map.empty, jobsById = jobs)
  }

}
