package agora.api.json

import io.circe.{Json, JsonObject}

import scala.compat.Platform

/**
  * Represents all the json paths in some json.
  *
  * Arrays are denoted as the wildcard "*" string.
  */
case class JPaths(paths: Map[String, JPaths]) {

  def isEmpty = paths.isEmpty

  override def toString = flatten.sorted.mkString(Platform.EOL)

  def flatten: List[String] = flattenPaths.map(_.mkString("."))

  def flattenPaths: List[List[String]] = {
    val iter = paths.flatMap {
      case (key, values) if values.isEmpty => List(key :: Nil)
      case (key, values)                   => values.flattenPaths.map(key :: _)
    }
    iter.toList
  }

  def add(other: JPaths): JPaths = {
    val missing = other.paths.filterKeys(k => !paths.contains(k))

    val merged: Map[String, JPaths] = paths.map {
      case entry @ (key, old) =>
        other.paths.get(key) match {
          case Some(otherValue) => (key, old.add(otherValue))
          case None             => entry
        }
    }
    JPaths(merged ++ missing)
  }
}

object JPaths {
  val Empty: JPaths = new JPaths(Map.empty)

  /**
    * Determine the JPaths for the given json. Simple values will not contain any paths
    * @param json the json to check
    * @return the JPaths for the given values
    */
  def apply(json: Json): JPaths = forJson(json)

  private def forObject(json: JsonObject): JPaths = {
    val map = json.toMap.mapValues(forJson)
    JPaths(map)
  }

  private def forArray(json: Vector[Json]): JPaths = {
    val arrayvalue = if (json.isEmpty) {
      new JPaths(Map.empty)
    } else {
      json.map(forJson).reduce(_ add _)
    }

    JPaths(Map("*" -> arrayvalue))
  }

  private def forJson(json: Json): JPaths = json.arrayOrObject(Empty, forArray, forObject)
}
