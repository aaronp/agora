package agora.exec.workspace

import java.nio.file.StandardOpenOption._
import java.nio.file.{OpenOption, Path}

import agora.exec.model.Upload
import agora.io.LowPriorityIOImplicits
import akka.stream.scaladsl.FileIO
import akka.stream.{IOResult, Materializer}
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.Future

/**
  * An interface for workspaces to use when writing down files
  */
trait UploadDao {

  /**
    * Save the upload locally
    *
    * @param upload
    * @param options
    * @return
    */
  def writeDownUpload(upload: Upload, options: Set[OpenOption] = UploadDao.DefaultWriteOptions)(implicit mat: Materializer): Future[(Long, Path)]

  def writeDown(inputFiles: List[Upload], options: Set[OpenOption] = UploadDao.DefaultWriteOptions)(implicit mat: Materializer): Future[List[(Long, Path)]]
}

object UploadDao {

  val DefaultWriteOptions: Set[OpenOption] = Set(CREATE, WRITE, TRUNCATE_EXISTING, SYNC)

  def apply(dir: Path) = new FileUploadDao(dir)

  class FileUploadDao(val dir: Path) extends UploadDao with LowPriorityIOImplicits with StrictLogging {
    require(dir.isDir, s"$dir is not a directory")

    override def toString = s"FileUploadDao($dir)"

    def writeDownUpload(upload: Upload, options: Set[OpenOption])(implicit mat: Materializer): Future[(Long, Path)] = {
      import mat._
      val Upload(name, src, _) = upload
      val dest = if (name.asPath.isAbsolute) {
        name.asPath
      } else {
        dir.resolve(name)
      }

      if (!dest.exists()) {
        val writeFut: Future[IOResult] = src.runWith(FileIO.toPath(dest, options))
        writeFut.onComplete {
          case res => logger.debug(s"Writing to $dest completed w/ $res")
        }
        writeFut.map { ioResult: IOResult =>
          (ioResult.count, dest)
        }
      } else {
        Future.successful(dest.size -> dest)
      }
    }

    def writeDown(inputFiles: List[Upload], options: Set[OpenOption] = UploadDao.DefaultWriteOptions)(implicit mat: Materializer): Future[List[(Long, Path)]] = {
      import mat._

      val futures = inputFiles.map {
        case upload @ Upload(name, src, _) =>
          writeDownUpload(upload, options)
      }
      if (futures.isEmpty) {
        Future.successful(Nil)
      } else {
        Future.sequence(futures)
      }
    }
  }

}
