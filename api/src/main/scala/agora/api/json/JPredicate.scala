package agora.api.json

import java.time.LocalDateTime

import agora.api.time.{DateTimeResolver, TimeCoords}
import com.typesafe.config.{ConfigFactory, ConfigRenderOptions}
import io.circe.Decoder.Result
import io.circe.generic.auto._
import io.circe.syntax._
import io.circe.{Json, _}

import scala.language.implicitConversions
import scala.util.Try

/**
  * A JPredicate is a predicate specifically for matching Json values.
  *
  * The predicate itself can also be represented as json
  */
sealed trait JPredicate { self =>

  /** @param json the json to match
    * @return true if this predicate matches
    */
  def matches(json: Json): Boolean

  /** @return the json representing this predicate
    */
  def json: Json

  def and(other: JPredicate, theRest: JPredicate*): JPredicate = new And(self :: other :: theRest.toList)

  def or(other: JPredicate, theRest: JPredicate*): JPredicate = new Or(self :: other :: theRest.toList)
}

object JPredicate {

  object implicits extends LowPriorityPredicateImplicits {

    implicit class JsonHelper(private val sc: StringContext) extends AnyVal {
      def json(args: Any*): Json = {
        val jsonString = ConfigFactory.parseString(sc.s(args: _*)).root.render(ConfigRenderOptions.concise().setJson(true))
        io.circe.parser.parse(jsonString).right.get
      }
    }

  }

  trait LowPriorityPredicateImplicits {

    implicit def stringAsJson(s: String) = Json.fromString(s)

    implicit def intAsJson(i: Int) = Json.fromInt(i)

    implicit class RichJson(field: String) {
      private implicit def predAsJFilter(p: JPredicate): JFilter = JFilter(field, p)

      def asJPath = JPath(field)

      def !(other: JPredicate): JFilter = Not(other)

      def =!=[J](value: J)(implicit ev: J => Json): JFilter = Not(Eq(value))

      def !==[J](value: J)(implicit ev: J => Json): JFilter = {
        =!=(value)
      }

      def ===[J](value: J)(implicit ev: J => Json): JFilter = Eq(value)

      def equalTo[J](value: J)(implicit ev: J => Json): JFilter = Eq(value)

      def before(time: String): JFilter = Before(time)

      def after(time: String): JFilter = After(time)

      def gt[J](value: J)(implicit ev: J => Json): JFilter = Gt(value)

      def lt[J](value: J)(implicit ev: J => Json): JFilter = Lt(value)

      def gte[J](value: J)(implicit ev: J => Json): JFilter = Gte(value)

      def lte[J](value: J)(implicit ev: J => Json): JFilter = Lte(value)

      def ~=(regex: String): JFilter = JRegex(regex)

      /** Assumes a simple json array at the given field, whose contents match each of the
        * given json elements
        *
        * @param items
        * @return
        */
      def includes[J](items: Set[J])(implicit ev: J => Json): JFilter = JIncludes(items.map(ev))

      def includes[J](first: J, theRest: J*)(implicit ev: J => Json): JFilter = includes(theRest.toSet + first)
    }

  }

  implicit object JPredicateFormat extends Encoder[JPredicate] with Decoder[JPredicate] {
    override def apply(c: HCursor): Result[JPredicate] = {
      import cats.syntax.either._

      // format: off
      c.as[And].
        orElse(c.as[Or]).
        orElse(c.as[Not]).
        orElse(c.as[Eq]).
        orElse(c.as[JRegex]).
        orElse(c.as[JIncludes]).
        orElse(c.as[Gt]).
        orElse(c.as[Gte]).
        orElse(c.as[Lt]).
        orElse(c.as[Lte]).
        orElse(c.as[Before]).
        orElse(c.as[After])
      // format: on
    }

    override def apply(a: JPredicate): Json = a match {
      case p: And       => p.asJson
      case p: Or        => p.asJson
      case p: Not       => p.asJson
      case p: Eq        => p.asJson
      case p: JRegex    => p.asJson
      case p: JIncludes => p.asJson

      case p: Gt  => p.asJson
      case p: Gte => p.asJson
      case p: Lt  => p.asJson
      case p: Lte => p.asJson

      case p: Before => p.asJson
      case p: After  => p.asJson
    }
  }

}

case class Or(or: List[JPredicate]) extends JPredicate {
  override def matches(json: Json) = or.exists(_.matches(json))

  override def json = Json.obj("or" -> Json.fromValues(or.map(_.json)))
}

object Or {
  def apply(first: JPredicate, second: JPredicate, theRest: JPredicate*): Or = Or(first :: second :: theRest.toList)
}

case class And(and: List[JPredicate]) extends JPredicate {
  override def matches(json: Json) = and.forall(_.matches(json))

  override def json = Json.obj("and" -> Json.fromValues(and.map(_.json)))
}

object And {
  def apply(first: JPredicate, second: JPredicate, theRest: JPredicate*): And = And(first :: second :: theRest.toList)
}

case class Not(not: JPredicate) extends JPredicate {
  override def matches(json: Json) = !(not.matches(json))

  override def json: Json = this.asJson
}

case class Eq(eq: Json) extends JPredicate {
  override def matches(json: Json) = json == eq

  override def json: Json = this.asJson
}

case class Before(before: String) extends TimePredicate(before, _ isBefore _) with JPredicate {
  override def json: Json = this.asJson
}

case class After(after: String) extends TimePredicate(after, _ isAfter _) with JPredicate {
  override def json: Json = this.asJson
}

abstract class TimePredicate(time: String, compare: (LocalDateTime, LocalDateTime) => Boolean) {
  private val adjust: DateTimeResolver = time match {
    case TimeCoords(f) => f
    case other         => sys.error(s"'$time' couldn't be parsed as a date-time adjustment: $other")
  }

  def matches(json: Json) = {
    json.asString.exists {
      case TimeCoords(valueAdjust) =>
        val now       = TimeCoords.nowUTC()
        val jsonTime  = valueAdjust(now)
        val reference = adjust(now)
        compare(jsonTime, reference)
      case _ => false
    }
  }
}

case class JRegex(regex: String) extends JPredicate {
  private val pattern = regex.r

  override def matches(json: Json) = json.asString.exists(v => pattern.findFirstIn(v).isDefined)

  override def json: Json = this.asJson
}

case class JIncludes(elements: Set[Json]) extends JPredicate {

  def contains(array: Vector[Json]): Boolean = elements.forall(array.contains)

  override def matches(json: Json) = json.asArray.exists(contains)

  override def json: Json = this.asJson
}

/**
  * This is an interesting scala question/problem ... we just have json numbers, so we don't know if they're ints,
  * longs, big decimals, etc.
  *
  * Presumably we want to avoid a costly bigdecimal conversion/comparison (or perhaps not ... I need to check the actual
  * overhead). But assuming we do, then we want subclasses to provide the minimum overhead. We shouldn't make them
  * e.g. specify "greater than or equal to" for each numeric type (BigDecimal and Long), but rather just use long and
  * then fall-back on big decimal if that's what we need.
  *
  *
  * @param value
  * @param bdCompare
  * @param longCompare
  */
sealed abstract class ComparablePredicate(value: Json, bdCompare: (BigDecimal, BigDecimal) => Boolean, longCompare: (Long, Long) => Boolean) extends JPredicate {
  //  TODO - we could compare the (private) Json instance types instead of using this 'toString' hack
  val requiresDec        = value.asNumber.map(_.toString).exists(_.contains("."))
  lazy val refLong       = asLong(value)
  lazy val refBigDecimal = asBigDecimal(value)

  private def asLong(json: Json) = {
    json.asNumber.flatMap(_.toLong).orElse {
      json.asString.flatMap(s => Try(s.toLong).toOption)
    }
  }

  private def asBigDecimal(json: Json) = {
    json.asNumber.flatMap(_.toBigDecimal).orElse {
      json.asString.flatMap(s => Try(BigDecimal(s)).toOption)
    }
  }

  override def matches(json: Json) = {
    val res = json.as[Json].right.map { (tea: Json) =>
      if (requiresDec) {
        (asBigDecimal(tea), refBigDecimal) match {
          case (Some(x), Some(y)) => bdCompare(x, y)
          case _                  => false
        }
      } else {
        (asLong(tea), refLong) match {
          case (Some(x), Some(y)) => longCompare(x, y)
          case _                  => false
        }
      }
    }
    res.right.getOrElse(false)
  }
}

import io.circe.Json

case class Gt(gt: Json) extends ComparablePredicate(gt, _ > _, _ > _) {
  override def json: Json = this.asJson
}

case class Gte(gte: Json) extends ComparablePredicate(gte, _ >= _, _ >= _) {
  override def json: Json = this.asJson
}

case class Lt(lt: Json) extends ComparablePredicate(lt, _ < _, _ < _) {
  override def json: Json = this.asJson
}

case class Lte(lte: Json) extends ComparablePredicate(lte, _ <= _, _ <= _) {
  override def json: Json = this.asJson
}
