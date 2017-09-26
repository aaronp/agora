package agora.api.exchange

import agora.BaseSpec
import agora.api.Implicits._

import scala.language.reflectiveCalls

class SubmitJobTest extends BaseSpec {

  "SubmitJob.withId" should {
    "overwrite previous values" in {
      "something".asJob.withId("first").withId("second").jobId shouldBe Option("second")
    }
  }
  "SubmitJob" should {
    "be able to get the json back" in {

      "foo".asJob shouldBe "foo".asJob
      "foo".asJob should not equal ("foo".asJob.add("key" -> "value"))
      "foo".asJob.add("key"                               -> "value") shouldBe ("foo".asJob.add("key" -> "value"))
    }
  }
}
