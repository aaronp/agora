package agora

import _root_.io.circe.Json.fromJsonObject
import _root_.io.circe._
import _root_.io.circe.syntax._
import _root_.io.circe.Decoder.Result
import agora.json.TypesByPath
import agora.core.FieldSelector
import cats.kernel.Semigroup

package object json {

  type TypeByPath  = (List[String], JType)
  type TypesByPath = Vector[TypeByPath]
  def newTypesByPath() = Vector[TypeByPath]()

  implicit object TypesByPathSemigroup extends Semigroup[TypesByPath] {
    override def combine(x: TypesByPath, y: TypesByPath): TypesByPath = {
      (x ++ y).distinct
    }
  }
  implicit class RichDec[T](val result: Result[T]) extends AnyVal {
    def orDecode[A <: T: Decoder](c: HCursor): Result[T] = {
      val aDec: Decoder[A] = implicitly[Decoder[A]]
      result.left.flatMap { _ =>
        aDec.tryDecode(c)
      }
    }
  }

  def jsonSelectorForPath(path: JPath): FieldSelector[Json, Json] = {
    FieldSelector.lift[Json, Json] { data =>
      path.apply(data).getOrElse(Json.Null)
    }
  }

  val JsonDiffAsDeltas = JsonDiffAsDataDiff.map { jsonDiff: JsonDiff =>
    import _root_.io.circe.generic.auto._
    jsonDiff.asJson
  }

  /**
    * merges the two json objects together, where array fields are concatenated
    *
    * @param targetJson the json which is the target of this operation
    * @param jsonToAdd  the json to add to the target json
    * @return the targetJson with common entries found in jsonToRemove removed
    */
  def deepMergeWithArrayConcat(targetJson: Json, jsonToAdd: Json): Json = {

    /**
      * try to concatenate two arrays. If they're both arrays, then lovey.
      * If we're merging a non-array w/ an array, then the value is added to the
      * array.
      * If we're merging an array w/ a non-array, then the existing value is prepended
      * to the array
      * Otherwise the target value is used.
      * @return
      */
    def notObjectMerge = {
      (targetJson.asArray, jsonToAdd.asArray) match {
        case (Some(leftArray), Some(rightArray)) => Json.fromValues(leftArray ++ rightArray)
        case (Some(leftArray), None)             => Json.fromValues(leftArray :+ jsonToAdd)
        case (None, Some(rightArray))            => Json.fromValues(targetJson +: rightArray)
        case _                                   => jsonToAdd
      }
    }

    (targetJson.asObject, jsonToAdd.asObject) match {
      case (Some(lhs), Some(rhs)) =>
        fromJsonObject(
          lhs.toList.foldLeft(rhs) {
            case (acc, (key, value)) =>
              val concatenatedOpt: Option[JsonObject] = for {
                leftArray  <- value.asArray
                rightArray <- rhs(key).flatMap(_.asArray)
              } yield {
                val arr = Json.fromValues(leftArray ++ rightArray)
                acc.add(key, arr)
              }

              def fallback = rhs(key).fold(acc.add(key, value)) { r =>
                acc.add(key, deepMergeWithArrayConcat(value, r))
              }

              concatenatedOpt.getOrElse(fallback)
          }
        )
      case _ => notObjectMerge
    }
  }

}
