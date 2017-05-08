package jabroni.api.exchange

import org.scalatest.{Matchers, WordSpec}

import scala.language.reflectiveCalls

class SubmissionDetailsTest extends WordSpec with Matchers {

  import SubmissionDetailsTest._

  "SubmissionDetails.withData/valueOf" should {
    "add the data to the json" in {

      import io.circe.generic.auto._

      val someData = SomeData(23, "meh")
      val sd = SubmissionDetails().withData(someData)
      val backAgain = sd.valueOf[SomeData]()
      backAgain shouldBe Right(someData)

    }
  }
}

object SubmissionDetailsTest {

  case class SomeData(foo: Int, bar: String)

}