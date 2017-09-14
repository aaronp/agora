package agora.api.time

import java.time.format.DateTimeFormatter
import java.time.{LocalDate, LocalDateTime, LocalTime, ZoneOffset}

import scala.concurrent.duration.FiniteDuration
import scala.util.{Success, Try}


object TimeCoords {

  private val SomeTimeAgo = """(\d+)\s+([a-z])s?\s+ago\s*""".r

  def nowUTC() = LocalDateTime.now(ZoneOffset.UTC)

  /**
    * Parses the text as a function from the given time to another [[LocalDateTime]]
    *
    * @param text
    * @return
    */
  def unapply(text: String): Option[LocalDateTime => LocalDateTime] = {
    text match {
      case FixedDateTime(time) => Option((_: LocalDateTime) => time)
      case FixedTime(time) => Option((input: LocalDateTime) => time.atDate(input.toLocalDate))
      case FixedDate(date) => Option((input: LocalDateTime) => date.atTime(input.toLocalTime))
      case Now() => Option((_: LocalDateTime) => nowUTC())
      case VariableTimeAgo(resolver) => Option(resolver)
      case TimeAgo(duration) => Option((_: LocalDateTime).minusNanos(duration.toNanos))
      case _ => None
    }
  }

  /** A text extractor to parse the text as [[FixedDateTime.formats]]
    *
    * @see https://docs.oracle.com/javase/8/docs/api/java/time/format/DateTimeFormatter.html
    */
  object FixedDateTime {
    val formats = List(
      DateTimeFormatter.ISO_LOCAL_DATE_TIME,
      DateTimeFormatter.ISO_OFFSET_DATE_TIME,
      DateTimeFormatter.ISO_INSTANT
    )

    def unapply(text: String): Option[LocalDateTime] = {
      val results = formats.iterator.map { formatter =>
        Try(formatter.parse(text))
      }
      results.collectFirst {
        case Success(result) => LocalDateTime.from(result)
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

  /** matches the text 'now'
    */
  object Now {
    def unapply(text: String) = {
      Option(text).collect {
        case n => n.toLowerCase == "now"
      }.getOrElse(false)
    }
  }

  /** Matches 'n years ago' ... 'n months ago'
    */
  object VariableTimeAgo {
    def unapply(text: String): Option[LocalDateTime => LocalDateTime] = {
      text.toLowerCase match {
        case SomeTimeAgo(n, "year") => Option((_: LocalDateTime).minusYears(n.toLong))
        case SomeTimeAgo(n, "month") => Option((_: LocalDateTime).minusMonths(n.toLong))
        case _ => None
      }
    }
  }

  /** Parses 'n [days|hours|minutes|seconds|weeks|fortnights|millis|milliseconds] ago' based on the datetime when parsed
    */
  object TimeAgo {
    def unapply(text: String): Option[FiniteDuration] = {
      import concurrent.duration._
      val ago: FiniteDuration = text.toLowerCase match {
        case SomeTimeAgo(n, "day") => n.toInt.day
        case SomeTimeAgo(n, "hour") => n.toInt.hour
        case SomeTimeAgo(n, "minute") => n.toInt.minute
        case SomeTimeAgo(n, "second") => n.toInt.second
        case SomeTimeAgo(n, "week") => n.toInt.day * 7
        case SomeTimeAgo(n, "fortnight") => n.toInt.day * 14
        case SomeTimeAgo(n, "milli") => n.toLong.millis
        case SomeTimeAgo(n, "millisecond") => n.toLong.millis
        case _ => null
      }
      Option(ago)
    }
  }

}