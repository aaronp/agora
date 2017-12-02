package agora.api.json

import io.circe.{Json, JsonObject}

import scala.compat.Platform

sealed trait JType

object JType {
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
  * Flattens a json object into a tree structure of types/keys:
  */
sealed trait TypeNode {
  def `type`: JType

  override def toString = flatten.sorted.mkString(Platform.EOL)

  final def flatten: Vector[String] = {
    flattenPaths.map {
      case (path, t) => path.mkString("", ".", s":$t")
    }
  }

  def flattenPaths: Vector[(List[String], JType)]
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

  private def forArray(json: Vector[Json]): TypeNode = TypeNodeArray(json.map(forJson).distinct)

  private def forJson(json: Json): TypeNode = {
    json.arrayOrObject(apply(JType(json)), forArray, forObject)
  }
}

case class TypeNodeObject(children: Map[String, TypeNode]) extends TypeNode {
  override val `type`: JType = ObjType

  override def flattenPaths: Vector[(List[String], JType)] = {
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
  override val `type`: JType = ArrayType

  override def flattenPaths: Vector[(List[String], JType)] = {
    if (children.isEmpty) {
      Vector(Nil -> NullType)
    } else {
      children.flatMap { value =>
        value.flattenPaths.map {
//        case (head :: tail, t) =>
//          (s"$head[]" :: tail) -> t
          case entry => entry
        }
      }
    }
  }
}

case class TypeNodeValue(override val `type`: JType) extends TypeNode {
  override def flattenPaths: Vector[(List[String], JType)] = Vector(Nil -> `type`)
}

/**
  * Represents all the json paths in some json.
  *
  * Arrays are denoted as the wildcard "*" string.
  */
//case class JPaths(paths: Map[String, JPaths]) {
//
//  def isEmpty = paths.isEmpty
//
//  override def toString = flatten.sorted.mkString(Platform.EOL)
//
//  def flatten: List[String] = {
//
//    flattenPaths.map(_.mkString("."))
//  }
//
//  def flattenPaths: List[List[String]] = {
//    val iter = paths.flatMap {
//      case (key, values) if values.isEmpty => List(key :: Nil)
//      case (key, values)                   => values.flattenPaths.map(key :: _)
//    }
//    iter.toList
//  }
//
//  def add(other: JPaths): JPaths = {
//    val missing = other.paths.filterKeys(k => !paths.contains(k))
//
//    val merged: Map[String, JPaths] = paths.map {
//      case entry @ (key, old) =>
//        other.paths.get(key) match {
//          case Some(otherValue) => (key, old.add(otherValue))
//          case None             => entry
//        }
//    }
//    JPaths(merged ++ missing)
//  }
//}
//
//object JPaths {
//  val Empty = new JPaths(Map.empty)
//
//  /**
//    * Determine the JPaths for the given json. Simple values will not contain any paths
//    * @param json the json to check
//    * @return the JPaths for the given values
//    */
//  def apply(json: Json): JPaths = forJson(json)
//
//  private def forObject(json: JsonObject): JPaths = {
//    val map = json.toMap.map {
//      case (key, value) => String(key, JType(value)) -> forJson(value)
//    }
//    JPaths(map)
//  }
//
//  private def forArray(json: Vector[Json]): JPaths = {
//    val arrayvalue = if (json.isEmpty) {
//      new JPaths(Map.empty)
//    } else {
//      json.map(forJson).reduce(_ add _)
//    }
//
//    JPaths(Map(String("*", ArrayType) -> arrayvalue))
//  }
//
//  private def forJson(json: Json): JPaths = {
//    //json.arrayOrObject(Empty, forArray, forObject)
//    json.arrayOrObject(new JPaths(Map(String("", JType(json)) -> Empty)), forArray, forObject)
//
//  }
//}
