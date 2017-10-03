package agora.api.time

import java.time.{LocalDate, LocalDateTime, LocalTime}

import agora.BaseSpec

class TimeCoordsTest extends BaseSpec {
  "TimeCoords.AsDuration" should {
    import concurrent.duration._
    val Scenarios = List(
      "2ms"          -> 2.millis,
      "2milli"       -> 2.millis,
      "2millis"      -> 2.millis,
      "2 millis"     -> 2.millis,
      "3s"           -> 3.seconds,
      "3 s"          -> 3.seconds,
      "3 sec"        -> 3.seconds,
      "3sec"         -> 3.seconds,
      "3second"      -> 3.seconds,
      "3seconds"     -> 3.seconds,
      "4m"           -> 4.minutes,
      "4min"         -> 4.minutes,
      "4 min"        -> 4.minutes,
      "4 minutes"    -> 4.minutes,
      "4 minute"     -> 4.minutes,
      "5h"           -> 5.hours,
      "5hr"          -> 5.hours,
      "5 hr"         -> 5.hours,
      "5hours"       -> 5.hours,
      "5 hour"       -> 5.hours,
      "5 hours"      -> 5.hours,
      "1 day"        -> 1.day,
      "1day"         -> 1.day,
      "1d"           -> 1.day,
      "1week"        -> 7.day,
      "1 week"       -> 7.day,
      "1 weeks"      -> 7.day,
      "1 fortnight"  -> 14.day,
      "1 fortnights" -> 14.day
    )
    Scenarios.foreach {
      case (input, expected) =>
        s"parse $input" in {
          val TimeCoords.AsDuration(actual) = input
          actual shouldBe expected
        }
    }
  }
  "TimeCoords.unapply" should {
    "match now" in {
      val TimeCoords(forTime) = "now"
      val date                = forTime(LocalDateTime.of(1977, 1, 1, 0, 0, 0))
      date shouldBe date
    }
    "return a time for the input date for 01:02:03" in {
      val TimeCoords(forTime) = "01:02:03"

      val date = forTime(LocalDateTime.of(1977, 1, 1, 0, 0, 0))

      date shouldBe LocalDateTime.of(1977, 1, 1, 1, 2, 3)
    }

    val scenarios: List[(String, (LocalDateTime) => LocalDateTime)] = List(
      ("1 days ago", (_: LocalDateTime).minusDays(1)),
      ("3 days ago", (_: LocalDateTime).minusDays(3)),
      ("3 Days Ago", (_: LocalDateTime).minusDays(3)),
      ("1 fortnight Ago", (_: LocalDateTime).minusDays(14)),
      ("1 week Ago", (_: LocalDateTime).minusDays(7)),
      ("1 hour ago", (_: LocalDateTime).minusHours(1)),
      ("2 Hours ago", (_: LocalDateTime).minusHours(2)),
      ("1 minute ago", (_: LocalDateTime).minusMinutes(1)),
      ("2 minutes ago", (_: LocalDateTime).minusMinutes(2)),
      ("2 seconds ago", (_: LocalDateTime).minusSeconds(2)),
      ("2 milliseconds ago", (_: LocalDateTime).minusNanos(2 * 1000000)),
      ("1 millisecond ago", (_: LocalDateTime).minusNanos(1000000)),
      ("1 milli ago", (_: LocalDateTime).minusNanos(1000000)),
      ("1 month ago", (_: LocalDateTime).minusMonths(1)),
      ("2 months ago", (_: LocalDateTime).minusMonths(2)),
      ("1 year ago", (_: LocalDateTime).minusYears(1))
    )

    scenarios.foreach {
      case (text, adjust) =>
        s"parse $text" in {
          text match {
            case TimeCoords(f) =>
              val point = LocalDateTime.of(1977, 1, 1, 1, 2, 3)
              f(point) shouldBe adjust(point)
          }
        }
    }
  }
  "TimeCoords.FixedTime.unapply" should {
    "match '10:15:30'" in {
      val TimeCoords.FixedTime(dateTime) = "10:15:30"
      dateTime shouldBe LocalTime.of(10, 15, 30)
    }
  }
  "TmeCoords.FixedDate.unapply" should {

    "match '2011-12-03'" in {
      val TimeCoords.FixedDate(dateTime) = "2011-12-03"
      dateTime shouldBe LocalDate.of(2011, 12, 3)
    }
  }
  "TimeCoords.FixedDateTime.unapply" should {
    "not match 'meh'" in {
      TimeCoords.FixedDateTime.unapply("meh") shouldBe empty
    }
    "match '2011-12-03T10:15:30'" in {
      val TimeCoords.FixedDateTime(dateTime) = "2011-12-03T10:15:30"
      dateTime shouldBe LocalDateTime.of(2011, 12, 3, 10, 15, 30)
    }
    "match '2011-12-03T10:15:30+01:00'" in {
      val TimeCoords.FixedDateTime(dateTime) = "2011-12-03T10:15:30+01:00"
      dateTime shouldBe LocalDateTime.of(2011, 12, 3, 10, 15, 30)
    }
    "match '2011-12-03T10:15:30Z'" in {
      val TimeCoords.FixedDateTime(dateTime) = "2011-12-03T10:15:30Z"
      dateTime shouldBe LocalDateTime.of(2011, 12, 3, 10, 15, 30)
    }

  }

}
