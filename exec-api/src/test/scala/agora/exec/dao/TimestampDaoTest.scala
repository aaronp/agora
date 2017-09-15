package agora.exec.dao

import java.nio.file.Path
import java.time.LocalDateTime

import agora.api.BaseSpec
import io.circe.generic.auto._

object TimestampDaoTest {

  case class Value(id: String, name: String)

  implicit object ValueId extends HasId[Value] {
    override def id(value: Value): String = value.id
  }

}

class TimestampDaoTest extends BaseSpec {

  import TimestampDaoTest._

  val Jan2 = LocalDateTime.of(2000, 1, 2, 3, 4, 5, 6789)

  case class Expect(time: Timestamp, from: Timestamp, to: Timestamp, findMe: Boolean) {
    def dao(dir: Path) = TimestampDao[Value](dir)

    def insert(dir: Path): Value = {
      val value = Value("first", s"entry for $time")
      dao(dir).save(value, time)

      withClue("a single entry should result in a timestamped file and an id file") {
        dir.nestedFiles.toList.size shouldBe 2
      }
      value
    }
  }

  def beforeTimes = List(
    Expect(Jan2, Jan2.minusNanos(2), Jan2.minusNanos(1), false),
    Expect(Jan2, Jan2.minusSeconds(2), Jan2.minusSeconds(1), false),
    Expect(Jan2, Jan2.minusMinutes(2), Jan2.minusMinutes(1), false),
    Expect(Jan2, Jan2.minusHours(2), Jan2.minusHours(1), false),
    Expect(Jan2, Jan2.minusDays(2), Jan2.minusDays(1), false),
    Expect(Jan2, Jan2.minusMonths(2), Jan2.minusMonths(1), false),
    Expect(Jan2, Jan2.minusYears(2), Jan2.minusYears(1), false)
  )

  def afterTimes = List(
    Expect(Jan2, Jan2.plusNanos(2), Jan2.plusNanos(3), false),
    Expect(Jan2, Jan2.plusSeconds(2), Jan2.plusSeconds(3), false),
    Expect(Jan2, Jan2.plusMinutes(2), Jan2.plusMinutes(3), false),
    Expect(Jan2, Jan2.plusHours(2), Jan2.plusHours(3), false),
    Expect(Jan2, Jan2.plusDays(2), Jan2.plusDays(3), false),
    Expect(Jan2, Jan2.plusMonths(2), Jan2.plusMonths(3), false),
    Expect(Jan2, Jan2.plusYears(2), Jan2.plusYears(3), false)
  )

  def inTimes = {
    for {
      before <- beforeTimes.map(_.from)
      after <- afterTimes.map(_.to)
    } yield {
      Expect(Jan2, before, after, true)
    }
  }

  def times = afterTimes ++ beforeTimes ++ inTimes


  "TimestampDao.find" should {
    times.foreach {
      case e@Expect(time, from, to, true) =>
        s"find $time given $from - $to" in {
          withDir { dir =>
            e.dao(dir).first shouldBe empty
            e.dao(dir).last shouldBe empty

            val value = e.insert(dir)

            e.dao(dir).find(from, to).toList should contain(value)

            e.dao(dir).first shouldBe Option(time)
            e.dao(dir).last shouldBe Option(time)

            // save another slightly later
            val nextValue = Value("second", s"entry for $time")
            e.dao(dir).save(nextValue, time.plusNanos(1))

            e.dao(dir).first shouldBe Option(time)
            e.dao(dir).last shouldBe Option(time.plusNanos(1))
          }
        }
      case e@Expect(time, from, to, false) =>
        s"not find $time given $from - $to" in {
          withDir { dir =>
            val value = e.insert(dir)
            e.dao(dir).find(from, to).toList should not contain (value)
          }
        }
    }

    "find saved values within a time range of the specific timestamp" in {
      withDir { dir =>
        val dao = TimestampDao[Value](dir)

        val value = Value("first", "alpha")
        dao.save(value, Jan2)

        withClue("a single entry should result in a timestamped file and an id file") {
          dir.nestedFiles.toList.size shouldBe 2
        }

        dao.find(Jan2, Jan2).toList should contain(value)
      }

    }
  }
}
