package agora.api.exchange.instances

import agora.BaseSpec
import agora.api.exchange.observer.TestObserver
import agora.api.exchange._
import agora.api.json.JPath
import io.circe.Json
import io.circe.optics.JsonPath
import agora.api.Implicits._

import scala.util.Success

class ExchangeInstanceTest extends BaseSpec {

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
      val job                     = "meh".asJob.withDetails(details)
      val (jobResp, stateWithJob) = state.submit(job)

      implicit val jsonJobPredicate                 = JobPredicate()
      val (matchResp, stateWithUpdatedSubscription) = stateWithJob.matches()

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
}
