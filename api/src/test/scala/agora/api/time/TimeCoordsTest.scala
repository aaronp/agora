package agora.api.time

import java.time.{LocalDate, LocalDateTime, LocalTime, ZoneOffset}

import agora.BaseSpec

class TimeCoordsTest extends BaseSpec {
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
