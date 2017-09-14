package agora.api

import java.time.LocalDateTime

package object time {

  type DateTimeResolver = LocalDateTime => LocalDateTime
}
