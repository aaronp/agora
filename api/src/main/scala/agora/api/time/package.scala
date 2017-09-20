package agora.api

import java.time.{ZoneId, ZoneOffset}

import agora.io.dao.{Timestamp, TimestampDao}

package object time {

  type Timestamp = agora.io.dao.Timestamp

  type Now = Timestamp


  implicit def ordering = agora.io.dao.ordering

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
  def now(zone: ZoneId = ZoneOffset.UTC): Now = TimestampDao.now(zone)
}
