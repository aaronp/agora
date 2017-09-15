package agora.exec

import java.time.{LocalDateTime, ZoneOffset}

package object dao {

  type Timestamp = LocalDateTime
  implicit val ordering = Ordering.by[Timestamp, Long](_.toEpochSecond(ZoneOffset.UTC))

}
