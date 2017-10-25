package agora.exec.events

import java.nio.file.Path

import agora.api.JobId
import agora.api.json.AgoraJsonImplicits
import agora.api.time._
import agora.io.dao.instances.{FileIdDao, FileTimestampDao}
import agora.io.dao.{FromBytes, HasId, IdDao, Persist, TimestampDao, ToBytes}
import com.typesafe.scalalogging.StrictLogging
import io.circe.generic.auto._

import scala.concurrent.Future
import scala.util.Try

/**
  * The idea here is to support writing down of jobs.
  *
  * We want to write down [[ReceivedJob]]s to disk and link to them from other events
  *
  */
case class EventDao(rootDir: Path) extends SystemEventMonitor with AgoraJsonImplicits with StrictLogging {

  /**
    * The implicit resolution here is circle (w/ java8.time) to expose
    * an Encoder[ReceivedJob], then the [[AgoraJsonImplicits]] to get a
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
        instances.foreach { inst =>
          val deleted = inst.deleteBefore(timestamp)
          logger.info(s"Deleting ${deleted} ${inst.name} events before $timestamp")
        }
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

    /**
      * Utility for adding detail/filtering on the results
      * @return
      */
    def prepareResponse[T: HasId](original: List[T], verbose: Boolean, filter: JobFilter)(update: (T, Option[ReceivedJob]) => T): List[T] = {
      if (verbose || filter.nonEmpty) {

        if (filter.nonEmpty) {
          original.flatMap { value =>
            val id                              = HasId.instance[T].id(value)
            val jobDetails: Option[ReceivedJob] = receivedDao.get(id)
            if (filter.accept(jobDetails)) {
              Option(update(value, jobDetails))
            } else {
              None
            }
          }
        } else {
          original.map { value =>
            val id                              = HasId.instance[T].id(value)
            val jobDetails: Option[ReceivedJob] = receivedDao.get(id)
            update(value, jobDetails)
          }
        }
      } else {
        original
      }
    }

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
      case ReceivedBetween(from, to, filter) =>
        val found = receivedDao.findBetween(from, to).toList.sortBy(_.received)
        val response = if (filter.nonEmpty) {
          found.filter(filter.matches)
        } else {
          found
        }
        ReceivedBetweenResponse(response)
      case StartedBetween(from, to, verbose, filter) =>
        val found: List[StartedJob] = startedDao.findBetween(from, to).toList.sortBy(_.started)
        val response = prepareResponse(found, verbose, filter) {
          case (value, detailsOpt) => value.copy(details = detailsOpt)
        }
        StartedBetweenResponse(response)
      case CompletedBetween(from, to, verbose, filter) =>
        val found = completedDao.findBetween(from, to).toList.sortBy(_.completed)
        val response = prepareResponse(found, verbose, filter) {
          case (value, detailsOpt) => value.copy(details = detailsOpt)
        }
        CompletedBetweenResponse(response)
      case NotFinishedBetween(from, to, verbose, filter) =>
        val found = notFinishedBetween(from, to).toList.sortBy(_.started)
        val response = prepareResponse(found, verbose, filter) {
          case (value, detailsOpt) => value.copy(details = detailsOpt)
        }
        NotFinishedBetweenResponse(response)
      case NotStartedBetween(from, to, filter) =>
        val found: List[ReceivedJob] = notStartedBetween(from, to).toList.sortBy(_.received)
        val response = if (filter.nonEmpty) {
          found.filter(filter.matches)
        } else {
          found
        }
        NotStartedBetweenResponse(response)
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
  private[events] class Instance[T: ToBytes: FromBytes: HasId](val name: String) {
    def deleteBefore(timestamp: Timestamp): Int = {
      val countOpt = timestampReader.first.filterNot(_.isBefore(timestamp)).map { firstTime =>
        val values: Iterator[T] = timestampReader.find(firstTime, timestamp)
        timestampReader.removeBefore(timestamp)
        values.foldLeft(0) {
          case (i, value) =>
            val id = hasId.id(value)
            idsDao.remove(id)
            i + 1
        }
      }

      countOpt.getOrElse(0)
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

    private def timestampReader = TimestampDao[T](timestampDir)

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
