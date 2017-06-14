package agora.api.worker

import org.scalatest.{Matchers, WordSpec}

class WorkerDetailsTest extends WordSpec with Matchers {

  import WorkerDetailsTest._
  import io.circe.generic.auto._
  import io.circe.parser

  "WorkerDetails()" should {
    "include a path" in {
      WorkerDetails().path shouldBe Some("handler")
    }
  }
  "WorkerDetails +" should {
    "accumulate json data" in {
      val details = WorkerDetails(runUser = "Eleanor", id = "optional id here", name = "bob", location = HostLocation("nearby", 5)) + SomeData(123, "some text") + MoreData("meh")

      val Right(expected) = parser.parse(
        """{
          |  "name" : "bob",
          |  "id" : "optional id here",
          |  "path" : "handler",
          |  "runUser" : "Eleanor",
          |  "location" : {
          |    "host" : "nearby",
          |    "port" : 5
          |  },
          |  "someData" : {
          |    "intField" : 123,
          |    "textField" : "some text"
          |  },
          |  "moreData" : {
          |    "foo" : "meh"
          |  }
          |}""".stripMargin)

      details.aboutMe shouldBe expected

    }
  }

}

object WorkerDetailsTest {

  case class SomeData(intField: Int, textField: String)

  case class MoreData(foo: String)

}
