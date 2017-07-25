package agora.api.exchange

import agora.api.worker.SubscriptionKey
import org.scalatest.{FunSuite, Matchers, WordSpec}

class ExchangeStateTest extends WordSpec with Matchers {

  "ExchangeState.matches" should {
    "find which jobs match which subscriptions" in new TestData {
      // call the method under test
      val (matches, newState) = state.matches

      // both work subscriptions should match and thus be decremented
      newState.subscriptionsById shouldBe Map[SubscriptionKey, (WorkSubscription, Int)]("foo" -> (s1, 0), "bar" -> (s2, 1))

      matches should contain only (
        MatchNotification("j1", j1, List(("foo", s1, 0))),
        MatchNotification("j2", j2, List(("bar", s2, 1)))
      )
    }

    "not match subscriptions with no available slots" in new TestData {
      val empty = Map[SubscriptionKey, (WorkSubscription, Int)]("just has the one" -> (s1, 1), "exhausted" -> (s2, 0))

      // call the method under test
      val (matches, newState) = state.copy(subscriptionsById = empty).matches
      val newSubscriptions    = newState.subscriptionsById

      // only 'just has the one' should match
      newSubscriptions shouldBe Map[SubscriptionKey, (WorkSubscription, Int)]("just has the one" -> (s1, 0), "exhausted" -> (s2, 0))

      matches should contain only (
        MatchNotification("j1",
                          j1,
                          List(("just has the one", s1, 0)))
      )
    }

    "match composite subscriptions if the constituent subscriptions match" in new TestData {

      val empty = Map[SubscriptionKey, (WorkSubscription, Int)]("A" -> (s1, 1), "B" -> (s2, 1), "A and B" -> (compositeSubscription, 1))

      val composite = Map[SubscriptionKey, Compose]("allThree" -> Compose(compositeSubscription, "A", "B", "C"))

      val compositeState = new ExchangeState(subscriptionsById = empty, compositeSubscriptionsById = composite, jobsById = jobs)

      // call the method under test
      val (matches, newState) = compositeState.matches(MatchAll)
      val newSubscriptions    = newState.subscriptionsById

      // only 'just has the one' should match
      newSubscriptions shouldBe Map[SubscriptionKey, (WorkSubscription, Int)]("just has the one" -> (s1, 0), "exhausted" -> (s2, 0))

      matches should contain only (
        MatchNotification("j1",
                          j1,
                          List(("just has the one", s1, 0)))
      )
    }

    "not match composite subscriptions if any of the constituent subscriptions don't match" in new TestData {
      ???
    }
    "not match composite subscriptions if they aren't requesting work items" in new TestData {
      ???
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

  trait TestData {

    import agora.api.Implicits._

    val j1                    = "one".asJob
    val j2                    = "two".asJob
    val s1                    = WorkSubscription().append("name", "s1")
    val s2                    = WorkSubscription().append("name", "s2")
    val compositeSubscription = WorkSubscription().append("name", "composite")

    object MatchAll extends JobPredicate {
      override def matches(offer: SubmitJob, work: WorkSubscription): Boolean = true
    }
    implicit object TestMatcher extends JobPredicate {
      override def matches(offer: SubmitJob, work: WorkSubscription): Boolean = {
        if (offer.eq(j1)) work.eq(s1)
        else if (offer.eq(j2)) work.eq(s2)
        else false
      }
    }

    val jobs          = Map("j1"                                            -> j1, "j2"       -> j2)
    val subscriptions = Map[SubscriptionKey, (WorkSubscription, Int)]("foo" -> (s1, 1), "bar" -> (s2, 2))

    val state = new ExchangeState(subscriptionsById = subscriptions, compositeSubscriptionsById = Map.empty, jobsById = jobs)
  }
}
