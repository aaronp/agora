package agora.time

import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import java.time._

import scala.concurrent.duration.FiniteDuration
import scala.util.{Success, Try}

/**
  * Exposes the means for some basic parsing of date/times
  */
object TimeCoords {

  def nowUTC(): Timestamp = now()

  /**
    * Parses the text as a function from the given time to another [[Timestamp]]
    *
    * @param text
    * @return
    */
  def unapply(text: String): Option[Timestamp => Timestamp] = {
    text match {
      case FixedDateTime(time)        => Option((_: Timestamp) => time)
      case FixedTime(time)            => Option((input: Timestamp) => input.`with`(time))
      case FixedDate(date)            => Option((input: Timestamp) => input.`with`(date))
      case VariableTimeAgo(resolver)  => Option(resolver)
      case TimeAgo(duration)          => Option((_: Timestamp).minusNanos(duration.toNanos))
      case "now"                      => Option((date: Timestamp) => date)
      case "yesterday"                => Option((_: Timestamp).minusDays(1))
      case "tomorrow"                 => Option((_: Timestamp).plusDays(1))
      case AsDuration(futureDuration) => Option((_: Timestamp).plusNanos(futureDuration.toNanos))

      // last tuesday? next wednesday?
      case _ => None
    }
  }

  /** A text extractor to parse the text as [[FixedDateTime.formats]]
    *
    * @see https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html
    */
  object FixedDateTime {
    val formats: List[DateTimeFormatter] = List(
      DateTimeFormatter.ISO_LOCAL_DATE_TIME,
      DateTimeFormatter.ISO_OFFSET_DATE_TIME,
      DateTimeFormatter.ISO_INSTANT
    )

    def format(instant: ZonedDateTime): String = {
      DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(instant)
    }

    def unapply(text: String): Option[Timestamp] = {
      val results: Iterator[Try[TemporalAccessor]] = formats.iterator.map { formatter =>
        Try(formatter.parse(text))
      }
      results.collectFirst {
        case Success(result) =>
          try {
            ZonedDateTime.from(result)
          } catch {
            case _: DateTimeException => LocalDateTime.from(result).atZone(ZoneOffset.UTC)
          }
      }
    }
  }

  object FixedDate {
    val formats = List(
      DateTimeFormatter.ISO_LOCAL_DATE
    )

    def unapply(text: String): Option[LocalDate] = {
      val results = formats.iterator.map { formatter =>
        Try(formatter.parse(text))
      }
      results.collectFirst {
        case Success(result) => LocalDate.from(result)
      }
    }
  }

  /** A text extractor to parse the text as [[FixedTime.formats]]
    */
  object FixedTime {
    val formats = List(
      DateTimeFormatter.ISO_LOCAL_TIME
    )

    def unapply(text: String): Option[LocalTime] = {
      val results = formats.iterator.map { formatter =>
        Try(formatter.parse(text))
      }
      results.collectFirst {
        case Success(result) => LocalTime.from(result)
      }
    }
  }

  /** Matches 'n years ago' ... 'n months ago'
    */
  object VariableTimeAgo {
    private val SomeTimeAgo = """(\d+)\s+([a-z]+?)s?\s+ago\s*""".r

    def unapply(text: String): Option[Timestamp => Timestamp] = {
      text.toLowerCase match {
        case SomeTimeAgo(n, "year")  => Option((_: Timestamp).minusYears(n.toLong))
        case SomeTimeAgo(n, "month") => Option((_: Timestamp).minusMonths(n.toLong))
        case _                       => None
      }
    }
  }

  /** Parses 'n [days|hours|minutes|seconds|weeks|fortnights|millis|milliseconds] ago' based on the datetime when parsed
    */
  object TimeAgo {
    private val SomeTimeAgo = """(.*?)\s+?ago\s*""".r

    def unapply(text: String): Option[FiniteDuration] = {
      text.toLowerCase match {
        case SomeTimeAgo(AsDuration(d)) => Option(d)
        case _                          => None
      }
    }
  }

  object AsDuration {
    def unapply(text: String): Option[FiniteDuration] = {

      val Millis     = Set("ms", "milli", "millis", "millisecond", "milliseconds")
      val Seconds    = Set("s", "sec", "second", "seconds")
      val Minutes    = Set("m", "min", "minute", "minutes")
      val Hours      = Set("h", "hr", "hour", "hours")
      val Days       = Set("d", "day", "days")
      val Weeks      = Set("week", "weeks")
      val Fortnights = Set("fortnight", "fortnights")

      val IntQualifierR = "(\\d+)\\s*?([a-z]+?)".r
      import concurrent.duration._
      val duration: FiniteDuration = text.toLowerCase match {
        case IntQualifierR(n, units) if Millis.contains(units)     => n.toInt.millis
        case IntQualifierR(n, units) if Seconds.contains(units)    => n.toInt.second
        case IntQualifierR(n, units) if Minutes.contains(units)    => n.toInt.minute
        case IntQualifierR(n, units) if Hours.contains(units)      => n.toInt.hour
        case IntQualifierR(n, units) if Days.contains(units)       => n.toInt.day
        case IntQualifierR(n, units) if Weeks.contains(units)      => n.toInt.day * 7
        case IntQualifierR(n, units) if Fortnights.contains(units) => n.toInt.day * 14
        case _                                                     => null
      }
      Option(duration)
    }
  }

}
