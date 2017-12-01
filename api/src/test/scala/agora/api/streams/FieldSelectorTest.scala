package agora.api.streams

import agora.BaseSpec
import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax._

class FieldSelectorTest extends BaseSpec {
  import FieldSelectorTest._
  "FieldSelector instances" should {
    "be able to get fields from json" in {
      import agora.api.Implicits._
      val selector = FieldSelector.forPath("y".asJPath)
      selector.select(MyData(None, 42).asJson) shouldBe Json.fromInt(42)
    }
    "be able to get fields from some other data type" in {
      val selector = FieldSelector.lift[MyData, Int](_.y)
      selector.select(MyData(None, 3)) shouldBe 3
    }
  }
}

object FieldSelectorTest {

  case class MyData(x: Option[Int], y: Int)
}
