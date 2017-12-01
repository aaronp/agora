package agora.api.json

import agora.BaseSpec
import io.circe.Json

class JPathsTest extends BaseSpec {
  "JPaths.apply" should {
    "return empty paths for null or scalars" in {
      JPaths(Json.Null) shouldBe JPaths.Empty
      JPaths(Json.fromBoolean(true)) shouldBe JPaths.Empty
      JPaths(Json.fromInt(3)) shouldBe JPaths.Empty
      JPaths(Json.fromString("hi")) shouldBe JPaths.Empty
    }
    "return all the json paths for a given json object" in {
      val paths = JPaths(json"""{
              "base" : {
                "nestedBoolean" : true,
                "nestedArray" : [1,2,3],
                "objArray" : [
                  {
                    "foo" : "bar",
                     "deepNestedArray" : [
                        {
                          "fizz" : "buzz",
                          "buzz" : 12
                        },
                        {
                          "meh" : 1
                        },
                        3,
                        true
                     ]
                  },
                  {
                    "second" : "obj"
                  }
                ]
              },
              "ary" : [],
              "dbl" : 12.34
              }""")

      paths.flatten.sorted should contain inOrderOnly (
        "ary.*",
        "base.nestedArray.*",
        "base.nestedBoolean",
        "base.objArray.*.deepNestedArray.*.buzz",
        "base.objArray.*.deepNestedArray.*.fizz",
        "base.objArray.*.deepNestedArray.*.meh",
        "base.objArray.*.foo",
        "base.objArray.*.second",
        "dbl"
      )
    }
  }
}
