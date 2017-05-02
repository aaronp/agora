package jabroni.api.worker

import java.time.ZonedDateTime

/**
  * Representation of a job which was/has been/is running
  */
trait RunDetails[Output, ErrorOutput] {
  def started: ZonedDateTime

  def finished: Option[ZonedDateTime]

  def success: Boolean

  def outputKeys: Set[String]

  def errorKeys: Set[String]

  def outputFor(key: String): Option[Output]

  def errorFor(key: String): Option[ErrorOutput]

  def withOutput(key: String, output: Output): RunDetails[Output, ErrorOutput]

  def withError(key: String, output: ErrorOutput): RunDetails[Output, ErrorOutput]
}

object RunDetails {

  def start[Output, ErrorOutput](time: ZonedDateTime = ZonedDateTime.now): RunDetails[Output, ErrorOutput] = {
    Instance(time, None, false, Map.empty, Map.empty)
  }

  case class Instance[Output, ErrorOutput](override val started: ZonedDateTime,
                                           override val finished: Option[ZonedDateTime],
                                           override val success: Boolean,
                                           val outputsByKey: Map[String, Output],
                                           val errorsByKey: Map[String, ErrorOutput]
                                          ) extends RunDetails[Output, ErrorOutput] {
    override val outputKeys = outputsByKey.keySet
    override val errorKeys = errorsByKey.keySet

    def withOutput(key: String, output: Output) = copy(outputsByKey = outputsByKey.updated(key, output))

    def withError(key: String, output: ErrorOutput) = copy(errorsByKey = errorsByKey.updated(key, output))

    override def outputFor(key: String) = outputsByKey.get(key)

    override def errorFor(key: String) = errorsByKey.get(key)
  }

}
