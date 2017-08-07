package agora.api.worker

import io.circe.optics.JsonPath
import org.scalatest.{Matchers, WordSpec}

class WorkerDetailsTest extends WordSpec with Matchers {

  import WorkerDetailsTest._
  import io.circe.generic.auto._
  import io.circe.parser

  "WorkerDetails()" should {
    "include a path" in {
      WorkerDetails(HostLocation.localhost(1234)).path shouldBe "handler"
    }
  }
  "WorkerDetails.append" should {
    "append array values" in {
      val original = WorkerDetails(HostLocation.localhost(1234)).append("list", List(1, 2, 3))
      val appended = original.append("list", List(4, 5, 6))
      val listPath = JsonPath.root.list.arr
      val items = listPath.getOption(appended.aboutMe).get.map(_.asNumber.get.toInt.get)
      items shouldBe List(1, 2, 3, 4, 5, 6)
    }
    "append heterogeneous array values" in {
      val original = WorkerDetails(HostLocation.localhost(1234)).append("list", List(1, 2, 3))
      val appended = original.append("list", List(Map("four" -> 4)))
      val listPath = JsonPath.root.list.arr
      val items = listPath.getOption(appended.aboutMe).get.map { json =>
        json.as[Int].right.getOrElse {
          json.as[Map[String, Int]].right.get
        }
      }
      items shouldBe List(1, 2, 3, Map("four" -> 4))
    }
  }

  "WorkerDetails +" should {
    "accumulate json data" in {
      val original = WorkerDetails(runUser = "Eleanor", id = "optional id here", name = "bob", location = HostLocation("nearby", 5))
      val details = original + SomeData(123, "some text") + MoreData("meh")

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
