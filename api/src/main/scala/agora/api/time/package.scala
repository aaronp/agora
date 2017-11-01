package agora.api

import java.time.{ZoneId, ZoneOffset}

import agora.io.dao.{Timestamp, TimestampDao}

package object time {

  type Timestamp = agora.io.dao.Timestamp

  implicit def ordering = agora.io.dao.ordering

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
  def now(zone: ZoneOffset = ZoneOffset.UTC): Timestamp = TimestampDao.now(zone)

  def fromEpochNanos(epochNanos: Long, zone: ZoneOffset = ZoneOffset.UTC): Timestamp = {
    TimestampDao.fromEpochNanos(epochNanos, zone)
  }
}
