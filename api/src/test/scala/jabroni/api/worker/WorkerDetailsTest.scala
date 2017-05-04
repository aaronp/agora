package jabroni.api.worker

import org.scalatest.{Matchers, WordSpec}

class WorkerDetailsTest extends WordSpec with Matchers {

  import WorkerDetailsTest._
  import io.circe.generic.auto._
  import io.circe.parser

  "WorkerDetails +" should {
    "accumulate json data" in {
      val details = WorkerDetails(name = "bob", location = HostLocation("nearby", 5)) + SomeData(123, "some text") + MoreData("meh")
      val Right(expected) = parser.parse(
        """{
          |    "moreData" : {
          |        "foo" : "meh"
          |    },
          |    "someData" : {
          |        "intField" : 123,
          |        "textField" : "some text"
          |    },
          |    "runUser" : "bob",
          |    "location" : {
          |        "host" : "nearby",
          |        "port" : 5
          |    }
          |}""".stripMargin)

      details.aboutMe shouldBe expected

    }
  }

}

object WorkerDetailsTest {

  case class SomeData(intField: Int, textField: String)

  case class MoreData(foo: String)

}
