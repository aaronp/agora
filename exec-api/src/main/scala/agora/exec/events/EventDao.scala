package agora.exec.events

import java.nio.file.Path

import agora.api.json.JsonByteImplicits
import agora.io.dao.instances.FileTimestampDao
import agora.io.dao.{IdDao, Persist, TimestampDao}
import io.circe.generic.auto._
import io.circe.java8.time._

/**
  * The idea here is to support writing down of jobs.
  *
  * We want to write down [[ReceivedJob]]s to disk and link to them from other events
  *
  */
object EventDao extends JsonByteImplicits {

  /**
    * The implicit resolution here is circle (w/ java8.time) to expose
    * an Encoder[ReceivedJob], then the [[JsonByteImplicits]] to get a
    * [[agora.io.dao.ToBytes[ReceivedJob]] from the encoder, and finally
    * the persist which can use the 'ToBytes' to squirt the bytes into the file.
    */
  def writer: Persist.WriterInstance[ReceivedJob] = Persist.writer[ReceivedJob]

  def save(dir: Path, job: ReceivedJob) = {
    val file: Path = {
      implicit val saveJob = writer
      IdDao[ReceivedJob](dir).save(job.id, job)
    }

    // link to the saved id file
    {
      implicit val link                      = Persist.link[ReceivedJob](file)
      val dao: FileTimestampDao[ReceivedJob] = TimestampDao[ReceivedJob](dir)
      dao.save(job, job.received)
    }

  }
}
