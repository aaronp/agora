package agora.json

import cats.syntax.either._
import io.circe.Decoder.Result
import io.circe._
import io.circe.syntax._

import scala.collection.SeqView

sealed trait NumOp {
  def symbol: Symbol

  def eval(lhs: JsonNumber, rhs: JsonNumber): Option[Json]
}

object NumOp {

  lazy val ops = List(AddOp, SubstractOp, MultiplyOp, DivideOp, ModuloOp)

  implicit object NumOpJsonFormat extends Encoder[NumOp] with Decoder[NumOp] {
    override def apply(op: NumOp): Json = {
      op match {
        case AddOp       => Json.fromString("+")
        case SubstractOp => Json.fromString("-")
        case MultiplyOp  => Json.fromString("*")
        case DivideOp    => Json.fromString("/")
        case ModuloOp    => Json.fromString("%")
      }
    }

    override def apply(c: HCursor): Result[NumOp] = {
      c.as[String].flatMap {
        case "+"   => Right(AddOp)
        case "-"   => Right(SubstractOp)
        case "*"   => Right(MultiplyOp)
        case "/"   => Right(DivideOp)
        case "%"   => Right(ModuloOp)
        case other => Left(DecodingFailure(s"Expected one of +, -, *, / or %, but got $other", c.history))
      }
    }
  }

}

abstract class BaseNumOp(val symbol: Symbol) {
  def calculateLong(lhs: Long, rhs: Long): Long

  def calculateBigDecimal(lhs: BigDecimal, rhs: BigDecimal): BigDecimal

  def eval(lhsNum: JsonNumber, rhsNum: JsonNumber): Option[Json] = {
    (lhsNum.toLong, rhsNum.toLong) match {
      case (Some(n1), Some(n2)) =>
        Option(Json.fromLong(calculateLong(n1, n2)))
      case _ =>
        (lhsNum.toBigDecimal, rhsNum.toBigDecimal) match {
          case (Some(n1), Some(n2)) =>
            Option(Json.fromBigDecimal(calculateBigDecimal(n1, n2)))
          case _ => None
        }
    }
  }
}

case object ModuloOp extends BaseNumOp('%) with NumOp {
  override def calculateLong(lhs: Long, rhs: Long): Long = lhs % rhs

  override def calculateBigDecimal(lhs: BigDecimal, rhs: BigDecimal): BigDecimal = lhs % rhs
}

case object DivideOp extends BaseNumOp('/) with NumOp {
  override def calculateLong(lhs: Long, rhs: Long): Long = lhs / rhs

  override def calculateBigDecimal(lhs: BigDecimal, rhs: BigDecimal): BigDecimal = lhs / rhs
}

case object MultiplyOp extends BaseNumOp('*) with NumOp {
  override def calculateLong(lhs: Long, rhs: Long): Long = lhs * rhs

  override def calculateBigDecimal(lhs: BigDecimal, rhs: BigDecimal): BigDecimal = lhs * rhs
}

case object SubstractOp extends BaseNumOp('-) with NumOp {
  override def calculateLong(lhs: Long, rhs: Long): Long = lhs - rhs

  override def calculateBigDecimal(lhs: BigDecimal, rhs: BigDecimal): BigDecimal = lhs - rhs
}

case object AddOp extends BaseNumOp('+) with NumOp {
  override def calculateLong(lhs: Long, rhs: Long): Long = lhs + rhs

  override def calculateBigDecimal(lhs: BigDecimal, rhs: BigDecimal): BigDecimal = lhs + rhs
}

trait StrOp {
  def eval(lhs: String, rhs: String): Option[Json]
}

object StrOp {

  implicit object StrOpJsonFormat extends Encoder[StrOp] with Decoder[StrOp] {
    override def apply(op: StrOp): Json = {
      op match {
        case ConcatString => Json.fromString("concat")
      }
    }

    override def apply(c: HCursor): Result[StrOp] = {
      c.as[String].flatMap {
        case "concat" => Right(ConcatString)
        case other    => Left(DecodingFailure(s"Expected one of 'concat', but got $other", c.history))
      }
    }
  }

}

case object ConcatString extends StrOp {
  override def eval(lhs: String, rhs: String): Option[Json] = Option(Json.fromString(lhs + rhs))
}

sealed trait JExpression {
  def eval(json: Json): Option[Json]
}

object JExpression {

  def apply(path: JPath) = JPathExpression(path)

  def apply(result: Json) = JConstantExpression(Option(result))

  def apply(result: Option[Json]) = JConstantExpression(result)

  class JExpressionOps(exp: JExpression) {
    def add(other: JExpression) = JNumericExpression(exp, other, AddOp)

    def +(other: JExpression) = JNumericExpression(exp, other, AddOp)

    def subtract(other: JExpression) = JNumericExpression(exp, other, SubstractOp)

    def -(other: JExpression) = JNumericExpression(exp, other, SubstractOp)

    def multiply(other: JExpression) = JNumericExpression(exp, other, MultiplyOp)

    def *(other: JExpression) = JNumericExpression(exp, other, MultiplyOp)

    def divide(other: JExpression) = JNumericExpression(exp, other, DivideOp)

    def /(other: JExpression) = JNumericExpression(exp, other, DivideOp)

    def modulo(other: JExpression) = JNumericExpression(exp, other, ModuloOp)

    def %(other: JExpression) = JNumericExpression(exp, other, ModuloOp)

    def merge(other: JExpression) = JMergeExpression(exp, other)

    def concat(other: JExpression) = JStringExpression(exp, other, ConcatString)
  }

  trait LowPriorityJExpressionImplicits {
    implicit def asExpressionOps(exp: JExpression) = new JExpressionOps(exp)

    implicit def asJPathOps(exp: JPath) = new {
      def asExpression = JPathExpression(exp)
    }

    implicit def asJsonOps(json: Json) = new {
      def asExpression = JExpression(json)
    }
  }

  object implicits extends LowPriorityJExpressionImplicits

  implicit object JExpressionFormat extends Encoder[JExpression] with Decoder[JExpression] {
    override def apply(exp: JExpression): Json = {
      import io.circe.generic.auto._
      exp match {
        case value: JMergeExpression    => JMergeExpression.JsonFormat(value)
        case value: JNumericExpression  => JNumericExpression.JsonFormat(value)
        case value: JPathExpression     => value.asJson
        case value: JStringExpression   => JStringExpression.JsonFormat(value)
        case value: JConstantExpression => value.asJson
      }
    }

    override def apply(c: HCursor): Result[JExpression] = {
      import io.circe.generic.auto._
      // format: off

      c.as[JMergeExpression](JMergeExpression.JsonFormat).
        orElse(c.as[JNumericExpression](JNumericExpression.JsonFormat)).
        orElse(c.as[JPathExpression]).
        orElse(c.as[JStringExpression](JStringExpression.JsonFormat)).
        orElse(c.as[JConstantExpression])
      // format: on
    }
  }

}

case class JPathExpression(selectPath: JPath) extends JExpression {
  override def eval(json: Json): Option[Json] = selectPath.selectValue(json)
}

case class JConstantExpression(const: Option[Json]) extends JExpression {
  override def eval(json: Json): Option[Json] = const
}

case class JMergeExpression(lhs: JExpression, rhs: JExpression) extends JExpression {
  override def eval(json: Json) = {
    for {
      lhsValue <- lhs.eval(json)
      rhsValue <- rhs.eval(json)
    } yield deepMergeWithArrayConcat(lhsValue, rhsValue)
  }
}

object JMergeExpression {

  implicit object JsonFormat extends Encoder[JMergeExpression] with Decoder[JMergeExpression] {

    override def apply(exp: JMergeExpression): Json = {
      Json.obj("merge" -> Json.obj("lhs" -> exp.lhs.asJson, "rhs" -> exp.rhs.asJson))
    }

    override def apply(c: HCursor): Result[JMergeExpression] = {
      c.downField("merge").downField("lhs").as[JExpression].right.flatMap { lhsExpr =>
        c.downField("merge").downField("rhs").as[JExpression].right.map { rhs =>
          JMergeExpression(lhsExpr, rhs)
        }
      }
    }
  }

}

case class JStringExpression(lhs: JExpression, rhs: JExpression, op: StrOp) extends JExpression {
  override def eval(json: Json) = {
    for {
      lhsValue <- lhs.eval(json)
      rhsValue <- rhs.eval(json)
      lhsStr   <- lhsValue.asString
      rhsStr   <- rhsValue.asString
      value    <- op.eval(lhsStr, rhsStr)
    } yield value
  }
}

object JStringExpression {

  implicit object JsonFormat extends Encoder[JStringExpression] with Decoder[JStringExpression] {

    override def apply(exp: JStringExpression): Json = {
      Json.obj("concat" -> Json.obj("lhs" -> exp.lhs.asJson, "rhs" -> exp.rhs.asJson))
    }

    override def apply(c: HCursor): Result[JStringExpression] = {
      c.downField("concat").downField("lhs").as[JExpression].right.flatMap { lhsExpr =>
        c.downField("concat").downField("rhs").as[JExpression].right.map { rhs =>
          JStringExpression(lhsExpr, rhs, ConcatString)
        }
      }
    }
  }

}

case class JNumericExpression(lhs: JExpression, rhs: JExpression, op: NumOp) extends JExpression {

  override def eval(json: Json) = {
    for {
      lhsValue <- lhs.eval(json)
      rhsValue <- rhs.eval(json)
      lhsNum   <- lhsValue.asNumber
      rhsNum   <- rhsValue.asNumber
      value    <- op.eval(lhsNum, rhsNum)
    } yield value
  }
}

object JNumericExpression {

  implicit object JsonFormat extends Encoder[JNumericExpression] with Decoder[JNumericExpression] {

    override def apply(exp: JNumericExpression): Json = {
      Json.obj(exp.op.symbol.name -> Json.obj("lhs" -> exp.lhs.asJson, "rhs" -> exp.rhs.asJson))
    }

    override def apply(c: HCursor): Result[JNumericExpression] = {
      def asExpr(cursor: ACursor, op: NumOp) = {
        cursor.downField("lhs").as[JExpression].flatMap { lhs =>
          cursor.downField("rhs").as[JExpression].map { rhs =>
            JNumericExpression(lhs, rhs, op)
          }
        }
      }

      val parseResults: SeqView[JNumericExpression, Seq[_]] = NumOp.ops.view.flatMap {
        case op => asExpr(c.downField(op.symbol.name), op).right.toOption
      }

      parseResults.headOption match {
        case Some(expr) => Right(expr)
        case None       => Left(DecodingFailure("Couldn't parse numeric op", c.history))
      }
    }
  }

}
