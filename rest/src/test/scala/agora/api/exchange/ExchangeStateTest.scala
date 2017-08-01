package agora.api.exchange

import agora.api.Implicits._
import agora.api.json.JMatcher
import agora.api.worker.{SubscriptionKey, WorkerDetails}
import org.scalatest.{Matchers, WordSpec}

import scala.util.Success

class ExchangeStateTest extends WordSpec with Matchers {

  "ExchangeState.updateSubscription" should {
    "append additional subscription details" in {
      val original     = WorkSubscription(WorkerDetails().append("someArray", List(1, 2)), JMatcher.matchNone, JMatcher.matchNone)
      val initialState = new ExchangeState(subscriptionsById = Map("a" -> (original, 11), "b" -> (original, 12)))

      // call the method under test
      val (ack, newState) = initialState.updateSubscription("a", WorkerDetails().append("someArray", List(3, 4)).append("appended", true))

      implicit def pimpedDetails(wd: WorkerDetails) = new {
        def someArray = wd.aboutMe.hcursor.downField("someArray").as[List[Int]].right.get
      }

      ack.id shouldBe "a"
      ack.before.get.someArray shouldBe List(1, 2)
      ack.after.get.someArray shouldBe List(1, 2, 3, 4)

      // prove the json has been updated, though the number taken and subscriptions themselves are the same
      newState.subscriptionsById("b") shouldBe initialState.subscriptionsById("b")
      val (updated, 11) = newState.subscriptionsById("a")

      updated.jobMatcher shouldBe JMatcher.matchNone
      updated.submissionMatcher shouldBe JMatcher.matchNone
      newState.subscriptionsById.keySet shouldBe Set("a", "b")
      val updatedJson = updated.details.aboutMe
      val ints        = updatedJson.hcursor.downField("someArray").as[List[Int]].right.get
      ints shouldBe List(1, 2, 3, 4)

    }
    "return None if the subscription doesn't exist" in {

      val initialState     = new ExchangeState()
      val (ack, sameState) = initialState.updateSubscription("doesn't exist", WorkerDetails().append("foo", "bar"))
      sameState shouldBe initialState
      ack.id shouldBe "doesn't exist"
      ack.before shouldBe empty
      ack.after shouldBe empty

    }
  }

  "ExchangeState.subscribe" should {
    "replace existing subscriptions when given the same id" in {
      val original        = WorkSubscription().append("name", "first").append("removed", false).withSubscriptionKey("static key")
      val initialState    = new ExchangeState(subscriptionsById = Map("static key" -> (original, 1)))
      val newSubscription = WorkSubscription().append("name", "updated name").withSubscriptionKey("static key")

      // call the method under test
      val (ack, newState) = initialState.subscribe(newSubscription)

      ack.id shouldBe "static key"
      newState.subscriptionsById.keySet shouldBe Set("static key")
      val (backAgain, 1) = newState.subscriptionsById("static key")
      backAgain.details.name shouldBe Some("updated name")
      backAgain.details.subscriptionKey shouldBe Some("static key")
      backAgain.details.aboutMe.hcursor.downField("removed").failed shouldBe true
    }
  }

  "ExchangeState.cancel" should {
    "cancel known subscriptions" in {
      val state           = new ExchangeState(subscriptionsById = Map("a" -> (WorkSubscription(), 1), "b" -> (WorkSubscription(), 1)))
      val (ack, newState) = state.cancelSubscriptions(Set("b", "c"))
      state.subscriptionsById.keySet shouldBe Set("a", "b")
      newState.subscriptionsById.keySet shouldBe Set("a")
      ack.canceledSubscriptions shouldBe Map("b" -> true, "c" -> false)
    }
    "cancel composite subscriptions which contain the cancelled subscription" in {

      val state = new ExchangeState(
        subscriptionsById = Map("a"                -> (WorkSubscription(), 1), "b" -> (WorkSubscription(), 1)),
        compositeSubscriptionsById = Map("a and b" -> Compose(WorkSubscription(), Set("a", "b")))
      )

      val (ack, newState) = state.cancelSubscriptions(Set("b"))
      state.subscriptionsById.keySet shouldBe Set("a", "b")
      state.compositeSubscriptionsById.keySet shouldBe Set("a and b")

      newState.subscriptionsById.keySet shouldBe Set("a")
      newState.compositeSubscriptionsById shouldBe empty
      ack.canceledSubscriptions shouldBe Map("b" -> true, "a and b" -> true)
    }
    "cancel composite subscriptions" in {

      val state = new ExchangeState(
        subscriptionsById = Map("a"                -> (WorkSubscription(), 1), "b" -> (WorkSubscription(), 1)),
        compositeSubscriptionsById = Map("a and b" -> Compose(WorkSubscription(), Set("a", "b")))
      )

      val (ack, newState) = state.cancelSubscriptions(Set("a and b"))
      state.subscriptionsById.keySet shouldBe Set("a", "b")
      newState.subscriptionsById.keySet shouldBe Set("a", "b")
      newState.compositeSubscriptionsById shouldBe empty
      ack.canceledSubscriptions shouldBe Map("a and b" -> true)
    }
  }

  "ExchangeState.pending" should {
    "return the minimum of all constituent subscriptions for composite subscriptions" in {
      val subscriptions = newSubscriptions("B", "C").updated("A", (newSubscription("A"), 2))
      val comp          = Map("A and B" -> Compose(WorkSubscription(), Set("A", "B")))
      val state         = new ExchangeState(subscriptionsById = subscriptions, compositeSubscriptionsById = comp)
      state.pending("A and B") shouldBe 1
    }
  }
  "ExchangeState.request" should {
    "request additional entries from subscriptions" in {
      val subscriptions = newSubscriptions("A", "B", "C")
      val comp          = Map("A and B" -> Compose(WorkSubscription(), Set("A", "B")))
      val state         = new ExchangeState(subscriptionsById = subscriptions, compositeSubscriptionsById = comp)
      // verify initial state preconditions
      state.pending("A") shouldBe 1
      state.pending("B") shouldBe 1
      state.pending("C") shouldBe 1
      state.pending("A and B") shouldBe 1

      // call the method under test
      val Success((ack, aWithOneMore)) = state.request("A", 1)

      ack.updated shouldBe Map("A" -> RequestWorkUpdate(1, 2))
      state.pending("A") shouldBe 1
      aWithOneMore.pending("A") shouldBe 2
      aWithOneMore.pending("B") shouldBe 1
      aWithOneMore.pending("C") shouldBe 1
      aWithOneMore.pending("A and B") shouldBe 1
    }
    "request additional entries from all constituent subscriptions when a composite subscription is specified" in {
      val subscriptions = newSubscriptions("A", "B", "C")
      val comp          = Map("A and B" -> Compose(WorkSubscription(), Set("A", "B")))
      val state         = new ExchangeState(subscriptionsById = subscriptions, compositeSubscriptionsById = comp)
      // verify initial state preconditions
      state.pending("A") shouldBe 1
      state.pending("B") shouldBe 1
      state.pending("C") shouldBe 1
      state.pending("A and B") shouldBe 1

      // call the method under test
      val Success((ack, newState)) = state.request("A and B", 2)
      ack.updated shouldBe Map("A" -> RequestWorkUpdate(1, 3), "B" -> RequestWorkUpdate(1, 3))
      newState.pending("A") shouldBe 3
      newState.pending("B") shouldBe 3
      newState.pending("C") shouldBe 1
      newState.pending("A and B") shouldBe 3
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
    val s1                     = newSubscription("s1")
    val s2                     = newSubscription("s2")
    val neverMatchSubscription = newSubscription("i won't match any jobs")
    val compositeSubscription  = newSubscription("composite")

    object MatchAll extends JobPredicate {
      override def matches(offer: SubmitJob, work: WorkSubscription): Boolean = {
        offer != neverMatchJob && work != neverMatchSubscription
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

  def newSubscription(name: String) = WorkSubscription().append("name", name)

  def newSubscriptions(first: String, theRest: String*): Map[String, (WorkSubscription, Int)] = {
    val ids = theRest.toSet + first
    ids.map(id => (id, (newSubscription(id), 1))).toMap
  }
}
