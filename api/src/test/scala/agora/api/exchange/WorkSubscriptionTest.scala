package agora.api.exchange

import agora.BaseSpec
import agora.api.worker.HostLocation

import scala.language.reflectiveCalls

class WorkSubscriptionTest extends BaseSpec {

  "WorkSubscription.withPath" should {
    "use the given path" in {
      val original       = WorkSubscription(HostLocation.localhost(1234))
      val beforeLocation = original.details.location

      val ws = original.withPath("foo")
      ws.details.path shouldBe "foo"
      ws.details.location shouldBe beforeLocation
    }
  }
  "WorkSubscription.path" should {
    "have a default path" in {
      WorkSubscription(HostLocation.localhost(1234)).details.path shouldBe "handler"
    }
  }
  "WorkSubscription.matches" should {

    import agora.api.Implicits._

    val jobPath = (("value" gt 7) and ("value" lt 17)) or ("value" === 123)
    val sub = WorkSubscription(HostLocation.localhost(1234),
                               jobCriteria = jobPath,
                               submissionCriteria = ("topic" === "foo").asMatcher)

    "match jobs with work subscriptions" in {
      val details = SubmissionDetails().add("topic" -> "foo")
      sub.matches(Map("value" -> 8).asJob(details)) shouldBe true
      sub.matches(Map("value" -> 1).asJob(details)) shouldBe false
      sub.matches(Map("value" -> 8).asJob) shouldBe false
      sub.matches(Map("value" -> 8).asJob(SubmissionDetails().add("topic" -> "bar"))) shouldBe false
      sub.matches(Map("value" -> 17).asJob(details)) shouldBe false
      sub.matches(Map("value" -> 123).asJob(details)) shouldBe true
      sub.matches(Map("value" -> 121).asJob(details)) shouldBe false
    }
  }
}
