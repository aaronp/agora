package agora.api.exchange

import agora.BaseSpec
import agora.api.Implicits._
import agora.api.exchange.instances.ExchangeState
import agora.api.json.JPredicate
import agora.api.worker.{HostLocation, SubscriptionKey, WorkerDetails}

import scala.util.Success

class ExchangeStateTest extends BaseSpec {

  "ExchangeState.updateSubscription" should {
    "append additional subscription details" in {
      implicit def intAsRequested(n: Int): Requested = Requested(n)

      val original =
        WorkSubscription.forDetails(WorkerDetails(HostLocation.localhost(1234)).append("someArray", List(1, 2)),
                                    JPredicate.matchNone,
                                    JPredicate.matchNone)
      val initialState =
        new ExchangeState(subscriptionsById = Map("a" -> (original, Requested(11)), "b" -> (original, Requested(12))))

      // call the method under test
      val json     = mkDetails().append("someArray", List(3, 4)).append("appended", true).aboutMe
      val newState = initialState.updateSubscription(UpdateSubscription.append("a", json)).get

      implicit def pimpedDetails(wd: WorkerDetails) = new {
        def someArray = wd.aboutMe.hcursor.downField("someArray").as[List[Int]].right.get
      }

      // prove the json has been updated, though the number taken and subscriptions themselves are the same
      newState.subscriptionsById("b") shouldBe initialState.subscriptionsById("b")
      val (updated, FixedRequested(11)) = newState.subscriptionsById("a")

      updated.jobCriteria shouldBe JPredicate.matchNone
      updated.submissionCriteria shouldBe JPredicate.matchNone
      newState.subscriptionsById.keySet shouldBe Set("a", "b")
      val updatedJson = updated.details.aboutMe
      val ints        = updatedJson.hcursor.downField("someArray").as[List[Int]].right.get
      ints shouldBe List(1, 2, 3, 4)
    }

    "return None if the subscription doesn't exist" in {
      val initialState = new ExchangeState()
      initialState.updateSubscription(UpdateSubscription.append("doesn't exist", "foo", "bar")) shouldBe None
    }
  }

  "ExchangeState.subscribe" should {

    "updated existing subscriptions when given the same id" in {
      val original = mkSubscription()
        .append("name", "first")
        .append("removed", false)
        .withSubscriptionKey("static key")
      val initialState =
        new ExchangeState(subscriptionsById = Map("static key" -> (original, Requested(1))))
      val newSubscription =
        mkSubscription().append("name", "updated name").withSubscriptionKey("static key")

      // call the method under test
      val (ack, newState) = initialState.subscribe(newSubscription)

      ack.id shouldBe "static key"
      newState.subscriptionsById.keySet shouldBe Set("static key")
      val (backAgain, FixedRequested(1)) = newState.subscriptionsById("static key")
      backAgain.details.name shouldBe Some("updated name")
      backAgain.details.subscriptionKey shouldBe Some("static key")
      backAgain.details.aboutMe.hcursor.downField("removed").as[Boolean].right.get shouldBe false
    }
  }

  "ExchangeState.orElseState" should {
    import agora.api.Implicits._

    "return an exchange state with the 'orElse' jobs and non-empty work subscriptions" in {

      val orElseJob1 =
        "orElseJob1".asJob.orElse("foo" gte "bar").ensuring(_.submissionDetails.orElse.size == 1)
      val orElseJob2 = "orElseJob2".asJob
        .orElse("a" === "b")
        .orElse("c" === "d")
        .ensuring(_.submissionDetails.orElse.size == 2)
      val basicJob = "basicJob".asJob
      val initialState = new ExchangeState(
        subscriptionsById =
          Map("one left" -> (mkSubscription(), Requested(1)), "empty" -> (mkSubscription(), Requested(0))),
        jobsById = Map(
          "orElseJob1" -> orElseJob1,
          "orElseJob2" -> orElseJob2,
          "basicJob"   -> basicJob
        )
      )

      val expected = new ExchangeState(
        subscriptionsById = Map("one left" -> (mkSubscription(), Requested(1))),
        jobsById = Map(
          "orElseJob1" -> "orElseJob1".asJob.matching("foo" gte "bar"),
          "orElseJob2" -> "orElseJob2".asJob.matching("a" === "b").orElse("c" === "d")
        )
      )

      // call the method under test
      initialState.orElseState shouldBe Some(expected)

      val expected2 = new ExchangeState(subscriptionsById = Map("one left" -> (mkSubscription(), Requested(1))),
                                        jobsById = Map(
                                          "orElseJob2" -> "orElseJob2".asJob.matching("c" === "d")
                                        ))
      // call the method under test with the 'orElse' subscription
      initialState.orElseState.get.orElseState shouldBe Some(expected2)

      // finally the third 'orElse' state should be empty when no jobs are left
      initialState.orElseState.get.orElseState.get.orElseState shouldBe empty
    }
    "return None if there are no pending subscriptions" in {
      val orElseJob =
        "orElseJob1".asJob.orElse("foo" gte "bar").ensuring(_.submissionDetails.orElse.size == 1)
      val initialState = new ExchangeState(subscriptionsById = Map("empty" -> (mkSubscription(), Requested(0))),
                                           jobsById = Map("orElseJob" -> orElseJob))

      initialState.orElseState shouldBe empty
    }
  }

  "ExchangeState.cancel" should {
    "cancel known subscriptions" in {
      val state = new ExchangeState(
        subscriptionsById = Map("a" -> (mkSubscription(), Requested(1)), "b" -> (mkSubscription(), Requested(1))))
      val (ack, newState) = state.cancelSubscriptions(Set("b", "c"))
      state.subscriptionsById.keySet shouldBe Set("a", "b")
      newState.subscriptionsById.keySet shouldBe Set("a")
      ack.canceledSubscriptions shouldBe Map("b" -> true, "c" -> false)
    }
  }

  "ExchangeState.pending" should {
    val s = mkSubscription()
    val subscriptions = Map(
      "linkA" -> (s -> LinkedRequested(Set("four", "linkB"))),
      "four"  -> (s -> Requested(4)),
      "three" -> (s -> Requested(3)),
      "two"   -> (s -> Requested(2)),
      "linkB" -> (s -> LinkedRequested(Set("three", "two")))
    )

    val state = new ExchangeState(subscriptionsById = subscriptions)

    "increment referenced subscriptions" in new ReferencedTestData {
      newState.pending("vanilla") shouldBe 2
      newState.pending("workspace") shouldBe 2

      val Success((threeAck, threeState)) = newState.request("workspace", 1)
      threeAck shouldBe RequestWorkAck("workspace", 2, 3)
      threeState.pending("vanilla") shouldBe 3
      threeState.pending("workspace") shouldBe 3

      val Success((fourAck, fourState)) = threeState.request("vanilla", 1)
      fourAck shouldBe RequestWorkAck("vanilla", 3, 4)

      fourState.pending("vanilla") shouldBe 4
      fourState.pending("workspace") shouldBe 4

      // and our initial states should be unchanged
      newState.pending("vanilla") shouldBe 2
      newState.pending("workspace") shouldBe 2
      threeState.pending("vanilla") shouldBe 3
      threeState.pending("workspace") shouldBe 3
    }

    "return the fixed remaining subscriptions" in {
      state.pending("three") shouldBe 3
      state.pending("two") shouldBe 2
    }
    "return the minimum of all constituent subscriptions for linked subscriptions" in {
      state.pending("linkB") shouldBe 2
      state.pending("linkA") shouldBe 2
    }
    "return 0 for unknown subscriptions" in {
      state.pending("unknown") shouldBe 0
    }
  }
  "ExchangeState.request" should {
    "request additional entries from subscriptions" in {
      val subscriptions = newSubscriptions("A", "B", "C")
      val state         = new ExchangeState(subscriptionsById = subscriptions)
      // verify initial state preconditions
      state.pending("A") shouldBe 1
      state.pending("B") shouldBe 1
      state.pending("C") shouldBe 1

      // call the method under test
      val Success((ack, aWithOneMore)) = state.request("A", 1)

      ack shouldBe RequestWorkAck("A", 1, 2)
      state.pending("A") shouldBe 1
      aWithOneMore.pending("A") shouldBe 2
      aWithOneMore.pending("B") shouldBe 1
      aWithOneMore.pending("C") shouldBe 1
    }
  }
  "ExchangeState.matches" should {

    "decrement referenced subscriptions" in new ReferencedTestData {

      val (SubmitJobResponse(jobId), stateWithJob) =
        newState.submit("123".asJob.add("topic", "exec"))

      val (List(notification), matchedState) = stateWithJob.matches

      stateWithJob.pending("vanilla") shouldBe 2
      stateWithJob.pending("workspace") shouldBe 2

      // both 'workspace' and 'vanilla' should be decremented
      matchedState.pending("vanilla") shouldBe 1
      matchedState.pending("workspace") shouldBe 1

    }
    "find which jobs match which subscriptions" in new TestData {
      // call the method under test
      val (matches, newState) = state.matches

      // both work subscriptions should match and thus be decremented
      newState.subscriptionsById shouldBe Map[SubscriptionKey, (WorkSubscription, Requested)](
        "s1"              -> (s1, Requested(0)),
        "s2"              -> (s2, Requested(1)),
        "never match sub" -> (neverMatchSubscription, Requested(10)))

      newState.jobsById shouldBe Map("never match job" -> neverMatchJob)
      matches should contain only (
        MatchNotification("j1", j1, List(Candidate("s1", s1, 0))),
        MatchNotification("j2", j2, List(Candidate("s2", s2, 1)))
      )
    }

    "not match subscriptions with no available slots" in new TestData {
      val empty = Map[SubscriptionKey, (WorkSubscription, Requested)]("just has the one" -> (s1, Requested(1)),
                                                                      "exhausted" -> (s2, Requested(0)))

      // call the method under test
      val (matches, newState) = state.copy(subscriptionsById = empty).matches
      val newSubscriptions    = newState.subscriptionsById

      // only 'just has the one' should match
      newSubscriptions shouldBe Map[SubscriptionKey, (WorkSubscription, Requested)](
        "just has the one"                             -> (s1, Requested(0)),
        "exhausted"                                    -> (s2, Requested(0)))
      newState.jobsById shouldBe Map("j2"              -> j2,
                                     "never match job" -> neverMatchJob,
                                     "never match job" -> neverMatchJob)
      matches should contain only (
        MatchNotification("j1",
                          j1,
                          List(Candidate("just has the one", s1, 0)))
      )
    }

    "not match subscriptions if they aren't requesting work items" in new TestData {

      val missingSubscriptions = Map("s1" -> (s1, Requested(1)), "s2" -> (s2, Requested(0)))

      val stateOnlyMatchingJ1 =
        new ExchangeState(subscriptionsById = missingSubscriptions, jobsById = jobs)

      val (matches, newState) = stateOnlyMatchingJ1.matches

      newState.jobsById.keySet shouldBe Set("j2", "never match job")

      matches should contain only (
        MatchNotification("j1",
                          j1,
                          List(Candidate("s1", s1, 0)))
      )
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

    val jobs = Map("j1" -> j1, "j2" -> j2, "never match job" -> neverMatchJob)
    val subscriptions = Map("s1" -> (s1, Requested(1)),
                            "s2"              -> (s2, Requested(2)),
                            "never match sub" -> (neverMatchSubscription, Requested(10)))

    val state = new ExchangeState(subscriptionsById = subscriptions, jobsById = jobs)
  }

  trait ReferencedTestData {

    // start out just being able to execute stuff
    val vanillaExec = WorkSubscription
      .forDetails(mkDetails().withPath("/execute").withSubscriptionKey("vanilla"))
      .matchingSubmission(("topic" === "exec").asMatcher)

    val initialState = new ExchangeState(subscriptionsById = Map("vanilla" -> (vanillaExec, Requested(2))))

    // now we make available some data to a workspace, but w/ the same endpoint.
    // We reference the 'vanilla' subscription though, so matches will decrement 'vanilla', and 'take' requests will
    // increment it
    val workspace = vanillaExec
      .withSubscriptionKey("workspace")
      .append("files", List("foo.txt", "bar.txt"))
      .referencing("vanilla")

    val (WorkSubscriptionAck("workspace"), newState) = initialState.subscribe(workspace)

  }

  def newSubscription(name: String) = WorkSubscription.forDetails(mkDetails()).append("name", name)

  def newSubscriptions(first: String, theRest: String*): Map[String, (WorkSubscription, Requested)] = {
    val ids = theRest.toSet + first
    ids.map(id => (id, (newSubscription(id), Requested(1)))).toMap
  }

  def mkDetails(): WorkerDetails = WorkerDetails(HostLocation.localhost(1234))

  def mkSubscription() = WorkSubscription.forDetails(mkDetails())
}
