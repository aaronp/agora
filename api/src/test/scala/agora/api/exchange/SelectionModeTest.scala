package agora.api.exchange

import agora.BaseSpec
import agora.api.json.{JPart, JPath}
import agora.api.nextSubscriptionKey
import agora.api.worker.WorkerDetails

class SelectionModeTest extends BaseSpec {

  import SelectionModeTest._
  import io.circe.generic.auto._
  import io.circe.syntax._

  Seq(
    SelectionMode.first(),
    SelectionMode.all(),
    SelectionMode(3, false),
    SelectionMode.max(JPath(JPart("field"), JPart(1), JPart("meh")))
  ).foreach { selMode =>
    selMode.toString should {
      "be serializable to/from json" in {
        import io.circe.syntax._

        val json             = selMode.asJson
        val Right(backAgain) = json.as[SelectionMode]
        backAgain shouldBe selMode
      }
    }
  }

  "max selection mode" should {
    "pick the one with the biggest value at a given json path" in {

      val a                   = Status(List(Holder(5689), Holder(6)))
      val b                   = Status(List(Holder(10)))
      val c                   = Status(Nil)
      val d                   = Status(List(Holder(500), Holder(600), Holder(-1)))
      val path                = JPath("values", "1", "cpus")
      val mode: SelectionMode = SelectionMode.max(path)
      val input = List(a, b, c, d).zipWithIndex.map {
        case (bean, i) =>
          Candidate(nextSubscriptionKey, WorkSubscription.forDetails(WorkerDetails(bean.asJson)), i)
      }
      mode.select[List[Candidate]](input).map(_.remaining) shouldBe List(3)
      mode.select[List[Candidate]](input.init).map(_.remaining) shouldBe List(0)
      mode.select[List[Candidate]](Nil).map(_.remaining) shouldBe Nil
    }
  }
}

object SelectionModeTest {

  case class Holder(cpus: Int, anotherField: Int = 999)

  case class Status(values: List[Holder])

}
