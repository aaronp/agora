package agora.io

import java.time.{LocalDateTime, ZoneOffset}

/**
  * Contains some traits for writing data.
  *
  * It's not intended to be a full-fledged ACID database or support SQL queries -
  * just a means to put some basic functionality on top of the file system.
  *
  * # IDs
  *
  * The [[agora.io.dao.IdDao[Key, T]] allows data to be written and retrieved against a key
  *
  * # Timestamps
  *
  * The [[agora.io.dao.TimestampDao]] can write timestamps against a value, or retrieve a timestamp for a
  * value
  *
  * # Tags
  *
  * The [[agora.io.dao.TagDao]] can write tags against a value, or retrieve a tags for a
  * value
  *
  *
  */
package object dao {

  type Timestamp = LocalDateTime
  implicit val ordering = Ordering.by[Timestamp, Long](_.toEpochSecond(ZoneOffset.UTC))

}
