package agora.api.exchange

import agora.BaseSpec
import agora.api.Implicits._
import agora.api.exchange.bucket.{BucketKey, BucketValueKey}
import agora.api.exchange.instances.ExchangeState
import agora.api.exchange.observer.{ExchangeObserver, OnMatch, TestObserver}
import agora.api.json.{JPath, JPredicate, MatchNone}
import agora.api.worker.{HostLocation, SubscriptionKey, WorkerDetails}
import io.circe.Json
import io.circe.optics.JsonPath

import scala.util.Success

class ExchangeStateTest extends BaseSpec {

  implicit class RichPath(p: JPath) {
    def asKey(optional: Boolean = false) = BucketKey(p, optional)
  }

  "ExchangeState onMatchAction" should {
    "Update matched work subscriptions with the onMatchUpdaterAction specified by the submitted job" in {
      val observer = new TestObserver

      // create some workers to match
      val state = {

        val emptyState                              = ExchangeState(observer)
        val subscriptionOne                         = WorkSubscription.localhost(1111).withSubscriptionKey("first")
        val (WorkSubscriptionAck("first"), withOne) = emptyState.subscribe(subscriptionOne)

        val subscriptionTwo                          = WorkSubscription.localhost(2222).withSubscriptionKey("second")
        val (WorkSubscriptionAck("second"), withTwo) = withOne.subscribe(subscriptionTwo)

        val Success((_, s2)) = withTwo.request("first", 1)
        val Success((_, s3)) = s2.request("second", 1)
        s3
      }

      // call the method under test - submit the job, and the matched work subscription should have its json updated

      // create a job which will append some json to matched workers
      val details = SubmissionDetails()
        .add("removeMe" -> Json.fromString("This should be removed on match"))
        .appendJsonOnMatch(json"""{ "testSessionId" : "my session id" }""")
        .appendJsonOnMatch(json"""{ "anotherSessionId" : "another id" }""", JPath("foo"))
        .removeOnMatch("removeMe".asJPath)
        .removeOnMatch("doesntExist".asJPath) // try and remove some json which doesn't exist (trying to do this shouldn't throw an error)
      val job                                     = "meh".asJob.withDetails(details)
      val (jobResp, stateWithUpdatedSubscription) = state.submit(job)

      // verify the state
      println(observer)
      stateWithUpdatedSubscription.jobsById.isEmpty shouldBe true

      val Seq(Candidate(id, subscription, 0)) = observer.lastMatch().get.selection
      val (updatedSubscription, _)            = stateWithUpdatedSubscription.subscriptionsById(id)

      println(subscription)
      println(updatedSubscription)
      println(updatedSubscription.details)

      withClue("paranoia check that some other json path (bar) wasn't added just to verify our assertion logic") {
        JsonPath.root.bar.testSessionId.string.getOption(updatedSubscription.details.aboutMe) shouldBe None
      }
      withClue("'removeMe' should've been removed") {
        val original = state.subscriptionsById(id)._1
        JsonPath.root.removeMe.string.getOption(original.details.aboutMe) shouldBe Some(Json.fromString("This should be removed on match"))
        JsonPath.root.removeMe.string.getOption(updatedSubscription.details.aboutMe) shouldBe None
      }
      withClue("both 'sessionId' and 'foo.anotherSessionId' should've been appended") {
        JsonPath.root.testSessionId.string.getOption(subscription.details.aboutMe) shouldBe Some("my session id")
        JsonPath.root.testSessionId.string.getOption(updatedSubscription.details.aboutMe) shouldBe Some("my session id")
        JsonPath.root.foo.anotherSessionId.string.getOption(subscription.details.aboutMe) shouldBe Some("my session id")
        JsonPath.root.foo.anotherSessionId.string.getOption(updatedSubscription.details.aboutMe) shouldBe Some("my session id")
      }

    }
    "Update all matched work subscriptions with the onMatchUpdaterAction specified by the submitted job" in {
      val state = ExchangeState()

      val job = {
        val original = "meh".asJob.withDetails(SubmissionDetails().appendJsonOnMatch(json"""{ "sessionId" : "my session id" }"""))

        // specify here to return (match) ALL eligible workers
        original.withDetails(original.submissionDetails.withSelection(SelectionAll))
      }

    }
    "Append json in an orElse clause so data is only appended if a particular condition is met" in {
      val state = ExchangeState()
      val job   = "this job will create a new session".asJob.withDetails(SubmissionDetails().appendJsonOnMatch(json"""{ "sessionId" : "my session id" }"""))

    }
  }
  "ExchangeState bucketing" should {
    "create a new work bucket when one is specified in a job submission" in {
      val state         = ExchangeState()
      val job           = "meh".asJob
      val fooJob        = job.withDetails(job.submissionDetails.withBuckets(JPath("topic") -> Json.fromString("foo")))
      val (_, fooState) = state.submit(fooJob)
      fooState.bucketsByKey.keySet shouldBe Set(List(JPath("topic").asKey()))

      withClue("A different topic but with the same jpath should use the same bucket") {

        val barJob        = job.withDetails(job.submissionDetails.withBuckets(JPath("topic") -> Json.fromString("bar")))
        val (_, barState) = fooState.submit(barJob)
        barState.bucketsByKey.keySet shouldBe Set(List(JPath("topic").asKey()))
      }

    }
    "group new work subscriptions into all known existing buckets" in {
      val state         = ExchangeState()
      val job           = "meh".asJob
      val fooJob        = job.withDetails(job.submissionDetails.withBuckets(JPath("topic") -> Json.fromString("foo")))
      val (_, fooState) = state.submit(fooJob)
      fooState.bucketsByKey.keySet shouldBe Set(List(JPath("topic").asKey()))

      val newSubscriptionWithoutTopic       = WorkSubscription.localhost(1234).withSubscriptionKey("doesn't have topic")
      val newSubscriptionWithMatchingTopic  = WorkSubscription.localhost(1234).withSubscriptionKey("fooSubscription").append(json"""{ "topic" : "foo"}""")
      val newSubscriptionWithDifferentTopic = WorkSubscription.localhost(1234).withSubscriptionKey("mehSubscription").append(json"""{ "topic" : "meh"}""")
      val withSubscriptions = List(newSubscriptionWithoutTopic, newSubscriptionWithMatchingTopic, newSubscriptionWithDifferentTopic).foldLeft(fooState) {
        case (state, subscr) =>
          val (_, newState) = state.subscribe(subscr)
          newState
      }

      withSubscriptions.bucketsByKey(List(JPath("topic").asKey())).containsSubscription("fooSubscription") shouldBe true
      withSubscriptions.bucketsByKey(List(JPath("topic").asKey())).containsSubscription("mehSubscription") shouldBe true
      withSubscriptions.bucketsByKey(List(JPath("topic").asKey())).containsSubscription("doesn't have topic") shouldBe false
    }
    "move updated work subscriptions into new buckets and remove them from old ones" in {

      // 1) start with a work subscription (no buckets, as no jobs have yet been submitted)
      val stateWithSubscription = {
        val state = ExchangeState()
        val newSubscriptionWithMatchingTopic =
          WorkSubscription.localhost(1234).withSubscriptionKey("fooSubscription").append(json"""{ "topic" : "foo"}""").copy(submissionCriteria = MatchNone)
        val (_, stateWithTopic) = state.subscribe(newSubscriptionWithMatchingTopic)
        stateWithTopic
      }

      // 2) create a state which will be putting things into both 'topic' and 'id' buckets. The 'topic' one
      // should find the existing subscription
      val twoBucketsState = {
        val babeJob = "meh".asJob

        val fooJob        = babeJob.withDetails(babeJob.submissionDetails.withBuckets(JPath("topic") -> Json.fromString("foo")))
        val (_, fooState) = stateWithSubscription.submit(fooJob)

        val idJob           = babeJob.withDetails(babeJob.submissionDetails.withBuckets(JPath("myId") -> Json.fromString("123")))
        val (_, twoBuckets) = fooState.submit(idJob)
        twoBuckets
      }

      twoBucketsState.bucketsByKey.keySet shouldBe Set(List(JPath("topic").asKey()), List(JPath("myId").asKey()))

      twoBucketsState.bucketsByKey(List(JPath("topic").asKey())).containsSubscription("fooSubscription") shouldBe true

      val idsBucket = twoBucketsState.bucketsByKey(List(JPath("myId").asKey()))
      idsBucket.keysBySubscription shouldBe Map[SubscriptionKey, Set[BucketValueKey]]()
      idsBucket.subscriptionsByKey shouldBe Map[BucketValueKey, Set[SubscriptionKey]]()
    }
  }

  "ExchangeState.updateSubscription" should {
    "append additional subscription details" in {
      implicit def intAsRequested(n: Int): Requested = Requested(n)

      val observer = new TestObserver
      val original =
        WorkSubscription.forDetails(WorkerDetails(HostLocation.localhost(1234)).append("someArray", List(1, 2)), JPredicate.matchNone, JPredicate.matchNone)
      val initialState =
        new ExchangeState(observer = observer, subscriptionsById = Map("a" -> (original, Requested(11)), "b" -> (original, Requested(12))))

      // call the method under test
      val json = mkDetails().append("someArray", List(3, 4)).append("appended", true).aboutMe

      val newState = initialState.updateSubscription(UpdateSubscription.append("a", json)).get

      observer.lastUpdatedSubscription.map(_.subscriptionKey) shouldBe Some("a")

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
      val observer     = new TestObserver
      val initialState = new ExchangeState(observer = observer)
      initialState.updateSubscription(UpdateSubscription.append("doesn't exist", "foo", "bar")) shouldBe None
      observer.events shouldBe Nil
    }
  }

  "ExchangeState.subscribe" should {

    "updated existing subscriptions when given the same id" in {
      val original = mkSubscription()
        .append("name", "first")
        .append("removed", false)
        .withSubscriptionKey("static key")
      val observer = new TestObserver
      val initialState =
        new ExchangeState(observer = observer, subscriptionsById = Map("static key" -> (original, Requested(1))))
      val newSubscription: WorkSubscription = mkSubscription().append("name", "updated name").withSubscriptionKey("static key")

      // call the method under test
      val (ack, newState) = initialState.subscribe(newSubscription)

      observer.lastUpdated.isDefined shouldBe true
      observer.lastUpdatedSubscription().flatMap(_.subscription.details.name) shouldBe Some("updated name")

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

      val testObserver = ExchangeObserver()

      val initialState = new ExchangeState(
        observer = testObserver,
        subscriptionsById = Map("one left" -> (mkSubscription(), Requested(1)), "empty" -> (mkSubscription(), Requested(0))),
        jobsById = Map(
          "orElseJob1" -> orElseJob1,
          "orElseJob2" -> orElseJob2,
          "basicJob"   -> basicJob
        )
      )

      val expected = new ExchangeState(
        observer = testObserver,
        subscriptionsById = Map("one left" -> (mkSubscription(), Requested(1))),
        jobsById = Map(
          "orElseJob1" -> "orElseJob1".asJob.matching("foo" gte "bar"),
          "orElseJob2" -> "orElseJob2".asJob.matching("a" === "b").orElse("c" === "d")
        )
      )

      // call the method under test
      initialState.orElseState shouldBe Some(expected)

      val expected2 = new ExchangeState(
        observer = testObserver,
        subscriptionsById = Map("one left" -> (mkSubscription(), Requested(1))),
        jobsById = Map(
          "orElseJob2" -> "orElseJob2".asJob.matching("c" === "d")
        )
      )
      // call the method under test with the 'orElse' subscription
      initialState.orElseState.get.orElseState shouldBe Some(expected2)

      // finally the third 'orElse' state should be empty when no jobs are left
      initialState.orElseState.get.orElseState.get.orElseState shouldBe empty
    }
    "return None if there are no pending subscriptions" in {
      val orElseJob =
        "orElseJob1".asJob.orElse("foo" gte "bar").ensuring(_.submissionDetails.orElse.size == 1)
      val initialState = new ExchangeState(subscriptionsById = Map("empty" -> (mkSubscription(), Requested(0))), jobsById = Map("orElseJob" -> orElseJob))

      initialState.orElseState shouldBe empty
    }
  }

  "ExchangeState.cancel" should {
    "cancel known subscriptions" in {
      val state           = new ExchangeState(subscriptionsById = Map("a" -> (mkSubscription(), Requested(1)), "b" -> (mkSubscription(), Requested(1))))
      val (ack, newState) = state.cancelSubscriptions(Set("b", "c"))
      state.subscriptionsById.keySet shouldBe Set("a", "b")
      newState.subscriptionsById.keySet shouldBe Set("a")
      ack.cancelledSubscriptions shouldBe Map("b" -> true, "c" -> false)
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
      newState.subscriptionsById shouldBe Map[SubscriptionKey, (WorkSubscription, Requested)]("s1"              -> (s1, Requested(0)),
                                                                                              "s2"              -> (s2, Requested(1)),
                                                                                              "never match sub" -> (neverMatchSubscription, Requested(10)))

      newState.jobsById shouldBe Map("never match job" -> neverMatchJob)
      matches.map(_.copy(time = mehTime)) should contain only (
        OnMatch(mehTime, "j1", j1, List(Candidate("s1", s1, 0))),
        OnMatch(mehTime, "j2", j2, List(Candidate("s2", s2, 1)))
      )
    }

    "not match subscriptions with no available slots" in new TestData {
      val empty = Map[SubscriptionKey, (WorkSubscription, Requested)]("just has the one" -> (s1, Requested(1)), "exhausted" -> (s2, Requested(0)))

      // call the method under test
      val (matches, newState) = state.copy(subscriptionsById = empty).matches
      val newSubscriptions    = newState.subscriptionsById

      // only 'just has the one' should match
      newSubscriptions shouldBe Map[SubscriptionKey, (WorkSubscription, Requested)]("just has the one" -> (s1, Requested(0)),
                                                                                    "exhausted"        -> (s2, Requested(0)))
      newState.jobsById shouldBe Map("j2"                                                              -> j2, "never match job" -> neverMatchJob, "never match job" -> neverMatchJob)
      matches.map(_.copy(time = mehTime)) should contain only (
        OnMatch(mehTime,
                "j1",
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

      matches.map(_.copy(time = mehTime)) should contain only (
        OnMatch(mehTime,
                "j1",
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
      override def matches(offer: SubmitJob, work: WorkSubscription, requested: Int): Boolean = {
        offer != neverMatchJob && work != neverMatchSubscription
      }
    }

    implicit object TestMatcher extends JobPredicate {
      override def matches(offer: SubmitJob, work: WorkSubscription, requested: Int): Boolean = {
        if (offer.eq(j1)) work.eq(s1)
        else if (offer.eq(j2)) work.eq(s2)
        else false
      }
    }

    val jobs          = Map("j1" -> j1, "j2"                 -> j2, "never match job"                 -> neverMatchJob)
    val subscriptions = Map("s1" -> (s1, Requested(1)), "s2" -> (s2, Requested(2)), "never match sub" -> (neverMatchSubscription, Requested(10)))

    val state = new ExchangeState(subscriptionsById = subscriptions, jobsById = jobs)
  }

  trait ReferencedTestData {

    // start out just being able to execute stuff
    val vanillaExec = WorkSubscription
      .forDetails(mkDetails().withPath("/execute").withSubscriptionKey("vanilla"))
      .matchingSubmission(("topic" === "exec").asMatcher())
    val observer     = new TestObserver
    val initialState = new ExchangeState(observer = observer, subscriptionsById = Map("vanilla" -> (vanillaExec, Requested(2))))

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

  val mehTime = agora.api.time.now()

}
