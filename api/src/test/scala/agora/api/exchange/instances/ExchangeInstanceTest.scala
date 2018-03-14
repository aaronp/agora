package agora.api.exchange.instances

import agora.BaseApiSpec
import agora.api.Implicits._
import agora.api.exchange._
import agora.api.exchange.observer._
import agora.json.JPath
import io.circe.Json
import io.circe.optics.JsonPath

import scala.util.Success

class ExchangeInstanceTest extends BaseApiSpec {

  "ExchangeState onMatchAction" should {
    "Update matched work subscriptions with the onMatchUpdaterAction specified by the submitted job" in {
      new TestSetup {

        // call the method under test by submitting a job which specifies an 'onMatchUpdate'.
        // The matched work subscription should have its json updated
        exchange.submit(jobWithUpdateActions).futureValue

        // verify the state
        withClue(observer.toString) {

          observer.eventsInTheOrderTheyWereReceived should matchPattern {
            case List(
                OnSubscriptionCreated(_, Candidate("first", _, _)),
                OnSubscriptionCreated(_, Candidate("second", _, _)),
                OnSubscriptionRequestCountChanged(_, "first", 0, 1),
                OnSubscriptionRequestCountChanged(_, "second", 0, 1),
                OnJobSubmitted(_, job),
                OnSubscriptionRequestCountChanged(_, _, 1, 0),
                OnMatch(_, _, _, Seq(Candidate(_, _, 0)))
                ) =>
          }
        }
        exchange.currentState().jobsById.isEmpty shouldBe true

        val Seq(Candidate(id, subscription, 0)) = observer.lastMatch().get.selection
        val (updatedSubscription, _)            = exchange.currentState().subscriptionsById(id)

        withClue("paranoia check that some other json path (bar) wasn't added just to verify our assertion logic") {
          JsonPath.root.bar.testSessionId.string.getOption(updatedSubscription.details.aboutMe) shouldBe None
        }
        withClue("'removeMe' should've been removed") {
          JsonPath.root.removeMe.string.getOption(updatedSubscription.details.aboutMe) shouldBe None
        }
        withClue("both 'sessionId' and 'foo.anotherSessionId' should've been appended") {
          JsonPath.root.testSessionId.string.getOption(subscription.details.aboutMe) shouldBe Some("my session id")
          JsonPath.root.testSessionId.string.getOption(updatedSubscription.details.aboutMe) shouldBe Some("my session id")
          JsonPath.root.foo.anotherSessionId.string.getOption(subscription.details.aboutMe) shouldBe Some("another id")
          JsonPath.root.foo.anotherSessionId.string.getOption(updatedSubscription.details.aboutMe) shouldBe Some("another id")
        }
      }

    }
    "Update all matched work subscriptions with the onMatchUpdaterAction specified by the submitted job" in {
      new TestSetup {

        // call the method under test - this time matching both subscriptions
        exchange.submit(jobWithUpdateActions.withDetails(jobWithUpdateActions.submissionDetails.withSelection(SelectionMode.all()))).futureValue

        // verify the state
        withClue(observer.toString) {

          observer.eventsInTheOrderTheyWereReceived should matchPattern {
            case List(
                OnSubscriptionCreated(_, Candidate("first", _, _)),
                OnSubscriptionCreated(_, Candidate("second", _, _)),
                OnSubscriptionRequestCountChanged(_, "first", 0, 1),
                OnSubscriptionRequestCountChanged(_, "second", 0, 1),
                OnJobSubmitted(_, job),
                OnSubscriptionRequestCountChanged(_, _, 1, 0),
                OnSubscriptionRequestCountChanged(_, _, 1, 0),
                OnMatch(_, _, _, Seq(Candidate(_, _, 0), Candidate(_, _, 0)))
                ) =>
          }
        }
        exchange.currentState().jobsById.isEmpty shouldBe true

        val Seq(Candidate(firstId, subscription1, 0), Candidate(secondId, subscription2, 0)) = observer.lastMatch().get.selection
        val (updatedSubscription1, _)                                                        = exchange.currentState().subscriptionsById(firstId)
        val (updatedSubscription2, _)                                                        = exchange.currentState().subscriptionsById(secondId)

        withClue("'removeMe' should've been removed") {
          JsonPath.root.removeMe.string.getOption(updatedSubscription1.details.aboutMe) shouldBe None
          JsonPath.root.removeMe.string.getOption(updatedSubscription2.details.aboutMe) shouldBe None
        }
        withClue("both 'sessionId' and 'foo.anotherSessionId' should've been appended on both work subscriptions") {
          JsonPath.root.testSessionId.string.getOption(subscription1.details.aboutMe) shouldBe Some("my session id")
          JsonPath.root.testSessionId.string.getOption(updatedSubscription1.details.aboutMe) shouldBe Some("my session id")
          JsonPath.root.foo.anotherSessionId.string.getOption(subscription1.details.aboutMe) shouldBe Some("another id")
          JsonPath.root.foo.anotherSessionId.string.getOption(updatedSubscription1.details.aboutMe) shouldBe Some("another id")

          JsonPath.root.testSessionId.string.getOption(subscription2.details.aboutMe) shouldBe Some("my session id")
          JsonPath.root.testSessionId.string.getOption(updatedSubscription2.details.aboutMe) shouldBe Some("my session id")
          JsonPath.root.foo.anotherSessionId.string.getOption(subscription2.details.aboutMe) shouldBe Some("another id")
          JsonPath.root.foo.anotherSessionId.string.getOption(updatedSubscription2.details.aboutMe) shouldBe Some("another id")
        }
      }

    }
    "Append json in an orElse clause so data is only appended if a particular condition is met" in {

      new TestSetup {

        // call the method under test - this time matching both subscriptions
        val orElseJob = jobWithUpdateActions
          .withDetails(jobWithUpdateActions.withId("test job id").submissionDetails.withMatchCriteria("doesNot" === "exist"))
          .orElse(WorkMatcher("_subscriptionKey" === "second").appendJsonOnMatch(json""" {"or" : "else"} """))

        // call the method under test
        exchange.submit(orElseJob).futureValue

        exchange.currentState().jobsById.isEmpty shouldBe true

        withClue(observer.toString) {

          observer.eventsInTheOrderTheyWereReceived should matchPattern {
            case List(
                OnSubscriptionCreated(_, Candidate("first", _, _)),
                OnSubscriptionCreated(_, Candidate("second", _, _)),
                OnSubscriptionRequestCountChanged(_, "first", 0, 1),
                OnSubscriptionRequestCountChanged(_, "second", 0, 1),
                OnJobSubmitted(_, job),
                OnSubscriptionRequestCountChanged(_, "second", 1, 0),
                OnMatch(_, _, _, Seq(Candidate("second", _, 0)))
                ) =>
          }
        }

        val OnMatch(_, _, matchedJob, Seq(Candidate("second", subscription, 0))) = observer.lastMatch().get
        matchedJob shouldBe orElseJob
        val (updatedSubscription, _) = exchange.currentState().subscriptionsById("second")

        withClue("the second subscription should've been matched and updated") {
          JsonPath.root.or.string.getOption(subscription.details.aboutMe) shouldBe Some("else")
          JsonPath.root.or.string.getOption(updatedSubscription.details.aboutMe) shouldBe Some("else")
        }
        withClue("the first subscription should've been left alone") {
          val (first, _) = exchange.currentState().subscriptionsById("first")
          JsonPath.root.or.string.getOption(first.details.aboutMe) shouldBe None
        }
      }
    }
  }

  trait TestSetup {
    val observer = new TestObserver

    // create some workers to match
    val exchange = {

      val emptyState = ExchangeState(observer)
      val subscriptionOne =
        WorkSubscription.localhost(1111).withSubscriptionKey("first").append("removeMe", Json.fromString("This should be removed on match"))

      val (WorkSubscriptionAck("first"), withOne) = emptyState.subscribe(subscriptionOne)

      val subscriptionTwo                          = WorkSubscription.localhost(2222).withSubscriptionKey("second")
      val (WorkSubscriptionAck("second"), withTwo) = withOne.subscribe(subscriptionTwo)

      val Success((_, s2)) = withTwo.request("first", 1)
      val Success((_, s3)) = s2.request("second", 1)
      new ExchangeInstance(s3)(JobPredicate())
    }

    withClue("'removeMe' should be on the original subscription") {
      val original = exchange.currentState().subscriptionsById("first")._1.details.aboutMe
      JsonPath.root.removeMe.string.getOption(original) shouldBe Some("This should be removed on match")
    }

    // call the method under test by submitting a job which specifies an 'onMatchUpdate'.
    // The matched work subscription should have its json updated
    val jobWithUpdateActions = {
      // create a job which will append some json to matched workers
      val details = SubmissionDetails()
        .appendJsonOnMatch(json"""{ "testSessionId" : "my session id" }""")
        .appendJsonOnMatch(json"""{ "anotherSessionId" : "another id" }""", JPath("foo"))
        .removeOnMatch("removeMe".asJPath)
        .removeOnMatch("doesntExist".asJPath) // try and remove some json which doesn't exist (trying to do this shouldn't throw an error)

      "this doesn't matter as anything which can be converted to json can be a job".asJob.withDetails(details).withAwaitMatch(false)
    }
  }

}
