package jabroni.api

import org.scalatest.{Matchers, WordSpec}
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._

import scala.language.implicitConversions
import scala.language.reflectiveCalls

class SubmitJobTest extends WordSpec with Matchers with SubmitJob.LowPriorityImplicits {

  import SubmitJobTest._

  "SubmitJob" should {
    "be serializable to and from json" in {
      val original = SomeJobData(1, "two")
      val submit: SubmitJob = original.asJob()
      val submitJson = submit.asJson
      println(submitJson.noSpaces)
      val Right(backAgain) = decode[SubmitJob](submitJson.noSpaces)
      val Right(jobInputBackAgain) = decode[SomeJobData](backAgain.job.noSpaces)
      jobInputBackAgain shouldBe original
    }
  }

}

object SubmitJobTest {

  case class SomeJobData(x: Int, y: String)

}