package agora.api

import _root_.io.circe.Json.fromJsonObject
import _root_.io.circe._

package object json {

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

  /**
    * Removes common values from 'that' f
    *
    * @param from
    * @param that
    * @return
    */
  def deepRemove(from: Json, that: Json): Json = {
    (from.asObject, that.asObject) match {
      case (Some(lhs), Some(rhs)) =>
        fromJsonObject(
          lhs.toList.foldLeft(rhs) {
            case (acc, (key, value)) =>
              val removedArraysOpt: Option[JsonObject] = for {
                leftArray  <- value.asArray
                rightArray <- rhs(key).flatMap(_.asArray)
              } yield {
                // subtract arrays
                val newArray: Vector[Json] = leftArray.filterNot(rightArray.contains)
                val arr                    = Json.fromValues(newArray)
                acc.add(key, arr)
              }

              // they're not arrays - remove the
              def fallback: JsonObject = {
                if (rhs(key).isDefined) {
                  acc
                } else {
                  acc.add(key, value)
                }
              }

              removedArraysOpt.getOrElse(fallback)
          }
        )
      case _ => from
    }
  }
}
