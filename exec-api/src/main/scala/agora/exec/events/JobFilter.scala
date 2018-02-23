package agora.exec.events

/**
  * Represents a filter. We may change this to a sealed trait in future
  * @param contains
  */
case class JobFilter(contains: String = "") {
  def accept(details: Option[ReceivedJob]): Boolean = details.exists(matches)
  def matches(details: ReceivedJob): Boolean = {
    details.job.commandString.contains(contains)
  }

  def isEmpty: Boolean  = contains.isEmpty
  def nonEmpty: Boolean = contains.nonEmpty

}

object JobFilter {
  val empty = JobFilter("")
}
