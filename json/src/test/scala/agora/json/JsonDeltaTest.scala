package agora.json

import agora.BaseJsonSpec
import AgoraJsonImplicits._

class JsonDeltaTest extends BaseJsonSpec {
  "JsonDelta.update" should {
    val original =
      json"""{
            "a" : "b",
            "values" : [1,2]
            }"""

    val delta =
      JsonDelta(remove = List(JPath("values") :+ 2.inArray), append = json""" { "new" : true } """)

    "not change json when empty" in {
      JsonDelta().optionallyUpdate(original) shouldBe None
    }
    "remove multiple paths" in {

      val delta = JsonDelta(remove = List(
                              JPath("values") :+ 2.inArray,
                              JPath("values") :+ 1.inArray,
                              JPath("a")
                            ),
                            append = json""" { "new" : true } """)
      delta.optionallyUpdate(original) shouldBe Some(json"""{ "values" : [], "new" : true }""")
    }
    "remove and append values to json" in {
      delta.optionallyUpdate(original) shouldBe Some(json"""{
            "a" : "b",
            "values" : [1],
            "new" : true
            }""")

    }
    "return None if the update had no effect" in {
      // updating the updated value should have no effect
      delta.optionallyUpdate(original).flatMap(delta.optionallyUpdate) shouldBe None
    }
  }

}
