package agora.api.time

import java.time.{LocalDate, LocalDateTime, LocalTime, ZoneOffset}

import agora.api.BaseSpec

class TimeCoordsTest extends BaseSpec {
  "TimeCoords.unapply" should {
    "match now" in {
      implicit val localDateOrdering: Ordering[LocalDateTime] = Ordering.by(_.toEpochSecond(ZoneOffset.UTC))

      val TimeCoords(forTime) = "now"

      val start = TimeCoords.nowUTC()
      val date = forTime(LocalDateTime.of(1977, 1, 1, 0, 0, 0))
      val end = TimeCoords.nowUTC()

      date should be >= start
      date should be <= end
    }
    "return a time for the input date for 10:15:30" in {
      val TimeCoords(forTime) = "1:02:03"

      val date = forTime(LocalDateTime.of(1977, 1, 1, 0, 0, 0))

      date shouldBe LocalDateTime.of(1977, 1, 1, 1, 2, 3)
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
