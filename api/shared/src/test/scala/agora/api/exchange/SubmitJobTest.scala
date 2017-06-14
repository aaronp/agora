package agora.api.exchange

import agora.api.Implicits._
import org.scalatest.{Matchers, WordSpec}

import scala.language.reflectiveCalls


class SubmitJobTest extends WordSpec with Matchers {

  "SubmitJob.withId" should {
    "overwrite previous values" in {
      "something".asJob.withId("first").withId("second").jobId shouldBe Option("second")
    }
  }
  "SubmitJob" should {
    "be able to get the json back" in {

      "foo".asJob shouldBe "foo".asJob
      "foo".asJob should not equal ("foo".asJob.add("key" -> "value"))
      "foo".asJob.add("key" -> "value") shouldBe ("foo".asJob.add("key" -> "value"))
    }
  }
}
