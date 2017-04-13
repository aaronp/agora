package jabroni.api.exchange

import io.circe.Decoder.Result
import io.circe._
import io.circe.optics.JsonPath


sealed abstract class MatchMode(override val toString: String, val fanOut: Boolean)

// sends the work to the first matching eligible worker
case class MatchFirst(isFanOut: Boolean) extends MatchMode("match-all", isFanOut)

// sends the work to all eligible workers
case class MatchAll(isFanOut: Boolean) extends MatchMode("match-first", isFanOut)

object MatchMode {

  implicit object JsonFormat extends Encoder[MatchMode] with Decoder[MatchMode] {
    override def apply(mode: MatchMode): Json = {
      Json.obj("mode" -> Json.fromString(mode.toString), "fanOut" -> Json.fromBoolean(mode.fanOut))
    }

    override def apply(c: HCursor): Result[MatchMode] = {
      val res: Either[DecodingFailure, (String, Boolean)] = for {
        mode <- c.downField("mode").as[String].right
        fanOut <- c.downField("fanOut").as[Boolean].right
      } yield {
        (mode, fanOut)
      }

      res.right.flatMap {
        case ("match-all", fanOut) => Right(MatchAll(fanOut))
        case ("match-first", fanOut) => Right(MatchFirst(fanOut))
        case (value, _) =>
          val msg = s"Invalid match mode '${value}'"
          Left(DecodingFailure(msg, c.history))
      }
    }
  }

}