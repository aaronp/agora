package agora

import java.time.{LocalDateTime, ZoneOffset, ZonedDateTime}

package object time {

  type Timestamp = ZonedDateTime

  implicit val ordering = Ordering.by[Timestamp, Long](_.toEpochSecond)

  private type Now = Timestamp

  /**
    * Represents something which, given the current time (now), can return a resolved time
    *
    * e.g. a "5 minutes ago" resolver will subtract 5 minutes from the input 'now', or a fixed
    * date resolver would ignore 'now' and just return a fixed date.
    *
    */
  type DateTimeResolver = Now => Timestamp

  /** @return the current time in UTC
    */
  def now(zone: ZoneOffset = ZoneOffset.UTC) = ZonedDateTime.now(zone)

  def fromEpochNanos(epochNanos: Long, zone: ZoneOffset = ZoneOffset.UTC): Timestamp = {
    val second = epochNanos / 1000000
    val nanos  = (epochNanos % 1000000).toInt
    LocalDateTime.ofEpochSecond(second, nanos, zone).atZone(zone)
  }
}
