package agora.api.json

import io.circe.Decoder.Result
import io.circe._

sealed trait NumOp {
  def eval(lhs: JsonNumber, rhs: JsonNumber): Option[Json]
}
object NumOp {
  implicit object NumOpJsonFormat extends Encoder[NumOp] with Decoder[NumOp] {
    override def apply(op: NumOp): Json = {
      op match {
        case AddOp => Json.fromString("+")
        case SubstractOp => Json.fromString("-")
        case MultiplyOp => Json.fromString("*")
        case DivideOp => Json.fromString("/")
        case ModuloOp => Json.fromString("%")
      }
    }

    override def apply(c: HCursor): Result[NumOp] = {
      c.as[String].flatMap {
        case "+" => Right(AddOp)
        case "-" => Right(SubstractOp)
        case "*" => Right(MultiplyOp)
        case "/" => Right(DivideOp)
        case "%" => Right(ModuloOp)
        case other => Left(DecodingFailure(s"Expected one of +, -, *, / or %, but got $other", c.history))
      }
    }
  }
}

abstract class JEvalImpl() extends NumOp {
  def calculateLong(lhs: Long, rhs: Long): Long

  def calculateBigDecimal(lhs: BigDecimal, rhs: BigDecimal: BigDecimal

  override def eval(lhsNum: JsonNumber, rhsNum: JsonNumber): Option[Json] = {
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

case object ModuloOp extends JEvalImpl {
  override def calculateLong(lhs: Long, rhs: Long): Long = lhs % rhs

  override def calculateBigDecimal(lhs: BigDecimal, rhs: BigDecimal): BigDecimal = lhs % rhs
}
case object DivideOp extends JEvalImpl {
  override def calculateLong(lhs: Long, rhs: Long): Long = lhs / rhs

  override def calculateBigDecimal(lhs: BigDecimal, rhs: BigDecimal): BigDecimal = lhs / rhs
}

case object MultiplyOp extends JEvalImpl {
  override def calculateLong(lhs: Long, rhs: Long): Long = lhs * rhs

  override def calculateBigDecimal(lhs: BigDecimal, rhs: BigDecimal): BigDecimal = lhs * rhs
}

case object SubstractOp extends JEvalImpl {
  override def calculateLong(lhs: Long, rhs: Long): Long = lhs - rhs

  override def calculateBigDecimal(lhs: BigDecimal, rhs: BigDecimal): BigDecimal = lhs - rhs
}

case object AddOp extends JEvalImpl {
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
        case other => Left(DecodingFailure(s"Expected one of 'concat', but got $other", c.history))
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

  def apply(path : JPath) = JPathExpression(path)
  def apply(result: Json) = JConstantExpression(Option(result))
  def apply(result: Option[Json]) = JConstantExpression(result)

  class JExpressionOps(exp : JExpression) {
    def add(other : JExpression) = JNumericExpression(exp, other, AddOp)
    def +(other : JExpression) = JNumericExpression(exp, other, AddOp)
    def subtract(other : JExpression) = JNumericExpression(exp, other, SubstractOp)
    def -(other : JExpression) = JNumericExpression(exp, other, SubstractOp)
    def multiply(other : JExpression) = JNumericExpression(exp, other, MultiplyOp)
    def *(other : JExpression) = JNumericExpression(exp, other, MultiplyOp)
    def divide(other : JExpression) = JNumericExpression(exp, other, DivideOp)
    def /(other : JExpression) = JNumericExpression(exp, other, DivideOp)
    def modulo(other : JExpression) = JNumericExpression(exp, other, ModuloOp)
    def %(other : JExpression) = JNumericExpression(exp, other, ModuloOp)
  }
  trait LowPriorityJExpressionImplicits {
    implicit def asExpressionOpt(exp : JExpression) = new JExpressionOps(exp)
  }
  object implicits extends LowPriorityJExpressionImplicits

  implicit object JExpressionFormat extends Encoder[JExpression] with Decoder[JExpression] {
    override def apply(op: JExpression): Json = {
      ???
    }

    override def apply(c: HCursor): Result[JExpression] = {
      ???
    }
  }
}

case class JPathExpression(path: JPath) extends JExpression {
  override def eval(json: Json): Option[Json] = path.selectJson(json)
}

case class JConstantExpression(result: Option[Json]) extends JExpression {
  override def eval(json: Json): Option[Json] = result
}

case class JStringExpression(lhs: JExpression, rhs: JExpression, op: StrOp) extends JExpression {
  override def eval(json: Json) = {
    for {
      lhsValue <- lhs.eval(json)
      rhsValue <- rhs.eval(json)
      lhsStr <- lhsValue.asString
      rhsStr <- rhsValue.asString
      value <- op.eval(lhsStr, rhsStr)
    } yield value
  }
}

case class JNumericExpression(lhs: JExpression, rhs: JExpression, op: NumOp) extends JExpression {

  override def eval(json: Json) = {
    for {
      lhsValue <- lhs.eval(json)
      rhsValue <- rhs.eval(json)
      lhsNum <- lhsValue.asNumber
      rhsNum <- rhsValue.asNumber
      value <- op.eval(lhsNum, rhsNum)
    } yield value
  }
}
