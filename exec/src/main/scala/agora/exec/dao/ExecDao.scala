package agora.exec.dao

import java.nio.file.Path

import akka.stream.Materializer
import com.typesafe.scalalogging.StrictLogging
import io.circe.parser._
import agora.api.JobId
import agora.domain.io.LowPriorityIOImplicits
import agora.exec.model.{RunProcess, Upload}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Used by the [[agora.exec.run.RequestLoggingRunner]] to write down/read back jobs
  */
trait ExecDao {
  type SaveResult

  def save(jobId: JobId, run: RunProcess, inputs: List[Upload])(implicit mat: Materializer): Future[SaveResult]

  def get(jobId: JobId)(implicit mat: Materializer): Future[(RunProcess, List[Upload])]

  def findJobsByMetadata(stringToString: Map[JobId, JobId]): Future[Set[JobId]]

  def listMetadata(): Future[Map[String, List[String]]]

  def uploadDao(jobId: JobId): UploadDao
}

object ExecDao {
  def apply(dir: Path)(implicit ec: ExecutionContext) = new FileExecDao(dir)

  val RunProcessFileName = "runProcess.json"

  class FileExecDao(dir: Path)(implicit ec: ExecutionContext) extends ExecDao with LowPriorityIOImplicits with StrictLogging {

    private lazy val metadataDir = dir.resolve("metadata")

    private object MetadataLock

    override def listMetadata(): Future[Map[String, List[String]]] = {
      Future(walkMetadata())
    }

    def walkMetadata() = {
      val pairs = metadataDir.children.flatMap { keyDir =>
        if (keyDir.isDir) {
          val valueFiles = keyDir.children.filter(_.isDir)

          val values = valueFiles.flatMap { file =>
            file.lines.map(_.trim).collect {
              case KeyValue(_, value) => value
            }
          }

          val pair = keyDir.fileName -> values.toList.sorted
          Option(pair)
        } else {
          None
        }
      }

      pairs.toMap
    }

    override def findJobsByMetadata(metadata: Map[String, String]): Future[Set[JobId]] = {
      val ids: Set[Set[JobId]] = metadata.keySet.map { key =>
        findJobsForEntry(key, metadata(key))
      }
      val found = if (ids.isEmpty) {
        Set.empty[JobId]
      } else {
        ids.reduce(_ intersect _)
      }
      Future.successful(found)
    }

    private val KeyValue = "(.*):(.*)".r

    def findJobsForEntry(metadataKey: String, value: String): Set[JobId] = {
      val file = metadataDir.resolve(metadataKey).resolve(valueKey(value))
      if (file.isFile) {
        file.lines
          .map(_.trim)
          .collect {
            case KeyValue(id, `value`) => id
          }
          .toSet
      } else {
        Set.empty
      }
    }

    override type SaveResult = Path

    import io.circe.generic.auto._
    import io.circe.syntax._

    override def uploadDao(jobId: JobId) = UploadDao(dir.resolve(jobId).mkDir())

    def updateMetadata(key: String, value: String, jobId: JobId) = {
      metadataDir.resolve(key).resolve(valueKey(value)).createIfNotExists().append(s"$jobId:${value}\n")
    }

    def valueKey(value: String): String = value.filter(_.isLetterOrDigit).take(50)

    def saveMetadata(metadata: Map[String, String], jobId: JobId) = {
      MetadataLock.synchronized {
        metadata.foreach {
          case (key, value) => updateMetadata(key, value, jobId)
        }
      }
    }

    override def save(jobId: JobId, run: RunProcess, inputs: List[Upload])(implicit mat: Materializer): Future[Path] = {
      val ud = uploadDao(jobId)
      import mat._
      ud.writeDown(inputs).map { _ =>
        if (run.metadata.nonEmpty) {
          saveMetadata(run.metadata, jobId)
        }

        ud.dir.resolve(RunProcessFileName).text = run.asJson.noSpaces
        ud.dir
      }
    }

    override def get(jobId: JobId)(implicit mat: Materializer): Future[(RunProcess, List[Upload])] = {
      import mat._
      val opt: Option[Future[(RunProcess, List[Upload])]] = Option(dir.resolve(jobId)).filter(_.isDir).map { saveUnder =>
        val file = saveUnder.resolve(RunProcessFileName)

        val runProcessResult = decode[RunProcess](file.text)
        runProcessResult match {
          case Right(rp) =>
            UploadDao(saveUnder).read.flatMap { uploads =>
              Future.successful(rp -> uploads)
            }
          case Left(err) => Future.failed(err)
        }
      }
      opt.getOrElse(Future.failed(new Exception(s"Couldn't find job $jobId under $dir")))
    }
  }

}
