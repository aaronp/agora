package agora.time

trait TimeLowPriorityImplicits {

  implicit def asRichDateString(dateStr: String) = new {
    def asDate(ref: Timestamp = now()): Option[Timestamp] = {
      TimeCoords.unapply(dateStr).map(_(ref))
    }
  }
}

object TimeLowPriorityImplicits extends TimeLowPriorityImplicits
