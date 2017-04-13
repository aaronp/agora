package jabroni.api.json

import io.circe.Json
import io.circe.optics.{JsonPath, JsonTraversalPath}

/**
  * represents part of a json path
  * e.g.
  *
  * foo/2/{x == 3}/value
  *
  * would be represented as
  *
  * JField("foo") :: JPos(2) :: JFilterValue("x", 3) :: JField("value") :: Nil
  *
  * we may even support wildcards, etc.
  *
  *
  */
sealed trait JPart {

//  def advance(json: Json, path: JsonTraversalPath): JsonTraversalPath
//
//  def advance(json: Json, path: JsonPath = JsonPath.root): Either[JsonTraversalPath, JsonPath]
}

case class JField(name: String) extends JPart {

//  override def advance(json: Json, path: JsonTraversalPath): JsonTraversalPath = {
//    val next = path.selectDynamic(name)
//
//    ???
//  }
//
//  override def advance(json: Json, path: JsonPath) = {
//    val next = path.selectDynamic(name)
//    val opt: Option[Json] = next.json.getOption(json)
//    val isArray = opt.exists(_.isArray)
//    if (isArray) {
//      Left(path.each)
//    } else {
//      Right(next)
//    }
//  }
}

case class JPos(i: Int) extends JPart {
//  override def advance(json: Json, path: JsonTraversalPath) = ???
//
//  override def advance(json: Json, path: JsonPath) = {
//    val next = path.index(i)
//    val opt: Option[Json] = next.json.getOption(json)
//    opt -> Right(next)
//  }
}

case class JFilterValue(field: String, value: String) extends JPart {
//  override def advance(json: Json, path: JsonTraversalPath): (Option[Json], JsonTraversalPath) = ???
//
//  override def advance(json: Json, path: JsonPath): (Option[Json], Either[JsonTraversalPath, JsonPath]) = ???
}