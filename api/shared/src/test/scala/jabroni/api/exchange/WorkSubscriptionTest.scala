package jabroni.api.exchange

import org.scalatest.{Matchers, WordSpec}

import scala.language.reflectiveCalls

class WorkSubscriptionTest extends WordSpec with Matchers {

  "WorkSubscription.withPath" should {
    "use the given path" in {
      val original = WorkSubscription()
      val beforeLocation = original.details.location

      val ws = original.withPath("foo")
      ws.details.path shouldBe Some("foo")
      ws.details.location shouldBe beforeLocation
    }
  }
  "WorkSubscription.path" should {
    "have a default path" in {
      WorkSubscription().details.path shouldBe Some("handler")
    }
  }
  "WorkSubscription.matches" should {

    import jabroni.api.Implicits._

    val jobPath = (("value" gt 7) and ("value" lt 17)) or ("value" === 123)
    val sub = WorkSubscription(
      jobMatcher = jobPath,
      submissionMatcher = ("topic" === "foo").asMatcher)


    "match jobs with work subscriptions" in {
      val details = SubmissionDetails().add("topic" -> "foo")
      sub.matches(Map("value" -> 8).asJob(details)) shouldBe true
      sub.matches(Map("value" -> 1).asJob(details)) shouldBe false
      sub.matches(Map("value" -> 8).asJob(SubmissionDetails())) shouldBe false
      sub.matches(Map("value" -> 8).asJob(SubmissionDetails().add("topic" -> "bar"))) shouldBe false
      sub.matches(Map("value" -> 17).asJob(details)) shouldBe false
      sub.matches(Map("value" -> 123).asJob(details)) shouldBe true
      sub.matches(Map("value" -> 121).asJob(details)) shouldBe false
    }
  }
}
