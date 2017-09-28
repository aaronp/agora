package agora.api.json

import agora.BaseSpec
import agora.api.Implicits._

class JsonDeltaTest extends BaseSpec {
  "JsonDelta.update" should {
    val original =
      json"""{
            "a" : "b",
            "values" : [1,2]
            }"""

    val delta =
      JsonDelta(remove = List(JPath("values") :+ 2.inArray), append = json""" { "new" : true } """)

    "not change json when empty" in {
      JsonDelta().update(original) shouldBe None
    }
    "remove multiple paths" in {

      val delta = JsonDelta(remove = List(
                              JPath("values") :+ 2.inArray,
                              JPath("values") :+ 1.inArray,
                              JPath("a")
                            ),
                            append = json""" { "new" : true } """)
      delta.update(original) shouldBe Some(json"""{ "values" : [], "new" : true }""")
    }
    "remove and append values to json" in {
      delta.update(original) shouldBe Some(json"""{
            "a" : "b",
            "values" : [1],
            "new" : true
            }""")

    }
    "return None if the update had no effect" in {
      // updating the updated value should have no effect
      delta.update(original).flatMap(delta.update) shouldBe None
    }
  }

}