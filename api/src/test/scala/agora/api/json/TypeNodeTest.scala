package agora.api.json

import agora.BaseSpec
import io.circe.Json

class TypeNodeTest extends BaseSpec {
  "JPaths.apply" should {
    "return empty paths for null or scalars" in {
      TypeNode(Json.Null) shouldBe TypeNode(NullType)
      TypeNode(Json.fromBoolean(true)) shouldBe TypeNode(BooleanType)
      TypeNode(Json.fromInt(3)) shouldBe TypeNode(NumericType)
      TypeNode(Json.fromString("hi")) shouldBe TypeNode(TextType)
    }
    "return all the json paths for a given json object" in {
      val paths = TypeNode(json"""{
              "base" : {
                "nestedBoolean" : true,
                "nestedArray" : [1,2,3],
                "objArray" : [
                  {
                    "foo" : "bar",
                     "deepNestedArray" : [
                        {
                          "mysterious" : "buzz",
                          "buzz" : 12
                        },
                        {
                          "mysterious" : true,
                          "meh" : 1
                        },
                        {
                          "mysterious" : [1,2,3]
                        },
                        {
                          "mysterious" : [{ "nowItsAnObj" : true }]
                        },
                        {
                          "mysterious" : 19
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

//      println(paths)
      paths.flatten.sorted.foreach(println)

      val actual: Vector[String] = paths.flatten.sorted
      actual should contain inOrderOnly (
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
