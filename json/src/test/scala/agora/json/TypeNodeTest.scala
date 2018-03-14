package agora.json

import agora.BaseJsonSpec
import io.circe.Json

class TypeNodeTest extends BaseJsonSpec {
  "JPaths.apply" should {
    "return empty paths for null or scalars" in {
      TypeNode(Json.Null) shouldBe TypeNode(NullType)
      TypeNode(Json.fromBoolean(true)) shouldBe TypeNode(BooleanType)
      TypeNode(Json.fromInt(3)) shouldBe TypeNode(NumericType)
      TypeNode(Json.fromString("hi")) shouldBe TypeNode(TextType)
      TypeNode(json"""[]""") shouldBe TypeNodeArray(Vector.empty)
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

      val actual: Vector[String] = paths.flatten.sorted
      actual should contain inOrderOnly (
        "ary[]:ArrayType",
        "base.nestedArray[]:NumericType",
        "base.nestedBoolean:BooleanType",
        "base.objArray[].deepNestedArray[].buzz:NumericType",
        "base.objArray[].deepNestedArray[].meh:NumericType",
        "base.objArray[].deepNestedArray[].mysterious:BooleanType",
        "base.objArray[].deepNestedArray[].mysterious:NumericType",
        "base.objArray[].deepNestedArray[].mysterious:TextType",
        "base.objArray[].deepNestedArray[].mysterious[].nowItsAnObj:BooleanType",
        "base.objArray[].deepNestedArray[].mysterious[]:NumericType",
        "base.objArray[].deepNestedArray[]:BooleanType",
        "base.objArray[].deepNestedArray[]:NumericType",
        "base.objArray[].foo:TextType",
        "base.objArray[].second:TextType",
        "dbl:NumericType"
      )
    }
  }
}
