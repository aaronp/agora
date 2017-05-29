package jabroni.exec.dao

import java.nio.file.Path

import akka.stream.Materializer
import com.typesafe.scalalogging.StrictLogging
import io.circe.Decoder
import io.circe.parser._
import jabroni.api.JobId
import jabroni.domain.io.LowPriorityIOImplicits
import jabroni.exec.model.{RunProcess, Upload}

import scala.concurrent.Future

trait ExecDao {
  type SaveResult
  def save(jobId: JobId, run: RunProcess, inputs: List[Upload])(implicit mat: Materializer): Future[SaveResult]

  def get(jobId: JobId)(implicit mat: Materializer): Future[(RunProcess, List[Upload])]

  def uploadDao(jobId: JobId): UploadDao
}

object ExecDao {
  def apply(dir: Path): ExecDao = new FileExecDao(dir)

  val RunProcessFileName = "runProcess.json"

  class FileExecDao(dir: Path) extends ExecDao with LowPriorityIOImplicits with StrictLogging {

    override type SaveResult = Path
    import io.circe.generic.auto._
    import io.circe.syntax._

    override def uploadDao(jobId: JobId) = UploadDao(dir.resolve(jobId).mkDir())

    override def save(jobId: JobId, run: RunProcess, inputs: List[Upload])(implicit mat: Materializer): Future[Path] = {
      val ud = uploadDao(jobId)
      import mat._
      ud.writeDown(inputs).map { _ =>
        ud.dir.resolve(RunProcessFileName).text = run.asJson.noSpaces
        ud.dir
      }
    }

    override def get(jobId: JobId)(implicit mat: Materializer) = {
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
