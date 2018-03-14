package agora.json

import agora.BaseJsonSpec

class packageTest extends BaseJsonSpec {

  "deepMergeWithArrayConcat" should {
    "append two arrays" in {
      deepMergeWithArrayConcat(json"[1,2]", json"[3,4]") shouldBe json"[1,2,3,4]"
    }
    "append a value to an array" in {
      deepMergeWithArrayConcat(json"[1,2]", json"3") shouldBe json"[1,2,3]"
    }
    "append an array to a value" in {
      deepMergeWithArrayConcat(json"1", json"[2,3]") shouldBe json"[1,2,3]"
    }
    "append two nested arrays" in {
      deepMergeWithArrayConcat(json""" { "a" : "b", "c" : "d" } """, json""" { "a" : "new" } """) shouldBe
        json""" { "a" : "new", "c" : "d" } """
    }
    "override values" in {
      val array = deepMergeWithArrayConcat(json""" {"a" : [1,2]} """, json""" {"a" : [3,4]} """)

      array shouldBe json""" {"a" : [1,2,3,4]} """
    }
  }
}
