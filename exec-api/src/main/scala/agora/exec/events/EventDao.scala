package agora.exec.events

import java.nio.file.Path

import agora.api.JobId
import agora.api.json.JsonByteImplicits
import agora.api.time._
import agora.io.dao.instances.{FileIdDao, FileTimestampDao}
import agora.io.dao.{FromBytes, HasId, IdDao, Persist, TimestampDao, ToBytes}
import io.circe.generic.auto._

import scala.concurrent.Future
import scala.util.Try

/**
  * The idea here is to support writing down of jobs.
  *
  * We want to write down [[ReceivedJob]]s to disk and link to them from other events
  *
  */
case class EventDao(rootDir: Path) extends SystemEventMonitor with JsonByteImplicits {

  /**
    * The implicit resolution here is circle (w/ java8.time) to expose
    * an Encoder[ReceivedJob], then the [[JsonByteImplicits]] to get a
    * [[agora.io.dao.ToBytes[ReceivedJob]] from the encoder, and finally
    * the persist which can use the 'ToBytes' to squirt the bytes into the file.
    */
  private def writer: Persist.WriterInstance[ReceivedJob] = Persist.writer[ReceivedJob]

  private[events] val startedDao   = new Instance[StartedJob]("started")
  private[events] val receivedDao  = new Instance[ReceivedJob]("received")
  private[events] val completedDao = new Instance[CompletedJob]("completed")
  private[events] val sysEvents    = new Instance[StartedSystem]("sysEvents")
  private val instances = List(
    startedDao,
    receivedDao,
    completedDao,
    sysEvents
  )

  override def accept(msg: RecordedEvent): Unit = {
    msg match {
      case event: StartedSystem => sysEvents.save(event, event.startTime)
      case event: StartedJob    => startedDao.save(event, event.started)
      case event: ReceivedJob   => receivedDao.save(event, event.received)
      case event: CompletedJob  => completedDao.save(event, event.completed)
      case DeleteBefore(timestamp) =>
        instances.foreach(_.deleteBefore(timestamp))
    }
  }

  def notFinishedBetween(from: Timestamp, to: Timestamp): Iterator[StartedJob] = {
    val started: Iterator[StartedJob] = startedDao.findBetween(from, to)
    val completed: Stream[JobId]      = completedDao.findBetween(from, to).map(_.id).toStream
    started.filterNot(job => completed.contains(job.id))
  }

  def notStartedBetween(from: Timestamp, to: Timestamp): Iterator[ReceivedJob] = {
    val received: Iterator[ReceivedJob] = receivedDao.findBetween(from, to)
    val started: Stream[JobId]          = startedDao.findBetween(from, to).map(_.id).toStream
    received.filterNot(job => started.contains(job.id))
  }

  override def query(msg: EventQuery): Future[msg.Response] = {

    def handle = msg match {
      case FindJob(id) =>
        val started   = startedDao.get(id)
        val completed = completedDao.get(id)
        val took = for {
          s <- started
          c <- completed
        } yield {
          val duration = java.time.Duration.between(s.started, c.completed)
          duration.toMillis
        }
        FindJobResponse(receivedDao.get(id), started, completed, took)
      case ReceivedBetween(from, to) =>
        val found = receivedDao.findBetween(from, to).toList.sortBy(_.received)
        ReceivedBetweenResponse(found)
      case StartedBetween(from, to) =>
        val found = startedDao.findBetween(from, to).toList.sortBy(_.started)
        StartedBetweenResponse(found)
      case CompletedBetween(from, to) =>
        val found = completedDao.findBetween(from, to).toList.sortBy(_.completed)
        CompletedBetweenResponse(found)
      case NotFinishedBetween(from, to) =>
        val found = notFinishedBetween(from, to).toList.sortBy(_.started)
        NotFinishedBetweenResponse(found)
      case NotStartedBetween(from, to) =>
        val found = notStartedBetween(from, to).toList.sortBy(_.received)
        NotStartedBetweenResponse(found)
      case StartTimesBetween(from, to) =>
        val found = sysEvents.findBetween(from, to).toList.sortBy(_.startTime)
        StartTimesBetweenResponse(found)
      case FindFirst("started")   => FindFirstResponse(startedDao.first)
      case FindFirst("received")  => FindFirstResponse(receivedDao.first)
      case FindFirst("completed") => FindFirstResponse(completedDao.first)
      case FindFirst(unknown)     => sys.error(s"Unhandled FindFirst '$unknown'")
    }

    Future.fromTry(Try(handle)).asInstanceOf[Future[msg.Response]]
  }

  /**
    * Adds some DAO stuff for different events (starting, running, etc) to support time and id-based queries
    * for anything we want to write down/query
    *
    * @param name the dao name (started, received, completed, etc)
    */
  private[events] class Instance[T: ToBytes: FromBytes: HasId](name: String) {
    def deleteBefore(timestamp: Timestamp): Option[Int] = {
      timestampReader.first.filterNot(_.isBefore(timestamp)).map { firstTime =>
        val values: Iterator[T] = timestampReader.find(firstTime, timestamp)
        timestampReader.removeBefore(timestamp)
        values.foldLeft(0) {
          case (i, value) =>
            val id = hasId.id(value)
            idsDao.remove(id)
            i + 1
        }
      }
    }

    private val hasId        = implicitly[HasId[T]]
    private val idDir        = rootDir.resolve(name).resolve("ids")
    private val timestampDir = rootDir.resolve(name).resolve("times")

    private val idsDao: FileIdDao[T] = {
      implicit val saveJob = writer
      IdDao[T](idDir)
    }

    private def timestampsDao(jobFile: Path): FileTimestampDao[T] = {
      implicit val link = Persist.link[T](jobFile)
      TimestampDao[T](timestampDir)
    }

    private def timestampReader  = TimestampDao[T](timestampDir)
    def first: Option[Timestamp] = TimestampDao[T](timestampDir).first

    def findBetween(from: Timestamp, to: Timestamp) = {
      timestampReader.find(from, to)
    }

    def get(id: JobId) = idsDao.get(id)

    def remove(value: T, timestamp: Timestamp) = {
      val id = hasId.id(value)
      idsDao.remove(id)
      timestampReader.remove(value, timestamp)
    }

    def save(value: T, timestamp: Timestamp) = {
      val id   = hasId.id(value)
      val file = idsDao.save(id, value)
      // link to the saved id file
      timestampsDao(file).save(value, timestamp)
    }

  }

}
