package jabroni.api.exchange

import org.scalatest.{Matchers, WordSpec}

import scala.language.reflectiveCalls

class WorkSubscriptionTest  extends WordSpec with Matchers {

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
