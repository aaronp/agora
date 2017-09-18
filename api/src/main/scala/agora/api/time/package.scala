package agora.api

import java.time.{LocalDateTime, ZoneId, ZoneOffset}

import agora.io.dao.TimestampDao

package object time {

  type Now = LocalDateTime

  /**
    * Represents something which, given the current time (now), can return a resolved time
    *
    * e.g. a "5 minutes ago" resolver will subtract 5 minutes from the input 'now', or a fixed
    * date resolver would ignore 'now' and just return a fixed date.
    *
    */
  type DateTimeResolver = Now => LocalDateTime

  /** @return the current time in UTC
    */
  def now(zone: ZoneId = ZoneOffset.UTC): Now = TimestampDao.now(zone)
}
