package agora.json

import io.circe.{Json, JsonObject}

import scala.compat.Platform

/**
  * Represents a json type
  */
sealed trait JType

object JType {
  val Null  = NullType
  val Bool  = BooleanType
  val Num   = NumericType
  val Text  = TextType
  val Array = ArrayType
  val Obj   = ObjType

  def apply(json: Json): JType = {
    json.fold(
      NullType,
      _ => BooleanType,
      _ => NumericType,
      _ => TextType,
      _ => ArrayType,
      _ => ObjType
    )
  }
}

case object NullType extends JType

case object BooleanType extends JType

case object NumericType extends JType

case object TextType extends JType

case object ArrayType extends JType

case object ObjType extends JType

/**
  * Flattens a json object into a tree structure of paths and the types, e.g.:
  * {{{
  *   "ary[]:NullType",
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
      "dbl:NumericType",
  * }}}
  *
  * note that the same path can be seen w/ different value types (e.g. {x : 1} and then {x : true})
  */
sealed trait TypeNode {

  override def toString = flatten.sorted.mkString(Platform.EOL)

  /** Convenience method for turning the 'flattenPaths' into string descriptions
    * @return
    */
  final def flatten: Vector[String] = {
    flattenPaths.map {
      case (path, t) => path.mkString("", ".", s":$t")
    }
  }

  /**
    * @return all the paths (List[String] and the type for that path)
    */
  def flattenPaths: TypesByPath
}

object TypeNode {

  val Empty = TypeNodeValue(NullType)

  def apply(jType: JType) = TypeNodeValue(jType)

  /**
    * Determine the JPaths for the given json. Simple values will not contain any paths
    *
    * @param json the json to check
    * @return the JPaths for the given values
    */
  def apply(json: Json): TypeNode = forJson(json)

  private def forObject(json: JsonObject): TypeNode = {
    TypeNodeObject(json.toMap.mapValues(forJson))
  }

  private def forArray(json: Vector[Json]): TypeNode = {
    val arrayValues = json.map(forJson).distinct
    TypeNodeArray(arrayValues)
  }

  private def forJson(json: Json): TypeNode = {
    json.arrayOrObject(apply(JType(json)), forArray, forObject)
  }
}

case class TypeNodeObject(children: Map[String, TypeNode]) extends TypeNode {
  val `type`: JType = ObjType

  override def flattenPaths: TypesByPath = {
    children.toVector.flatMap {
      case (key, array: TypeNodeArray) =>
        array.flattenPaths.map {
          case (path, t) => (s"$key[]" :: path) -> t
        }
      case (key, values) =>
        values.flattenPaths.map {
          case (path, t) => (key :: path) -> t
        }
    }
  }
}

case class TypeNodeArray(children: Vector[TypeNode]) extends TypeNode {
  val `type`: JType = ArrayType

  override def flattenPaths: TypesByPath = {
    if (children.isEmpty) {
      Vector(Nil -> ArrayType)
    } else {
      children.flatMap(_.flattenPaths)
    }
  }
}

case class TypeNodeValue(val `type`: JType) extends TypeNode {
  override def flattenPaths: TypesByPath = Vector(Nil -> `type`)
}
