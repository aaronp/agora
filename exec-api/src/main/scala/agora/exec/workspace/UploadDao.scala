package agora.exec.workspace

import java.nio.file.StandardOpenOption._
import java.nio.file.{OpenOption, Path, Paths}

import agora.exec.model.Upload
import agora.io.LowPriorityIOImplicits
import akka.stream.Materializer
import akka.stream.scaladsl.FileIO
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.Future
import scala.util.Properties

/**
  * An interface for workspaces to use when writing down files
  */
trait UploadDao {

  def writeDown(inputFiles: List[Upload], options: Set[OpenOption] = UploadDao.DefaultWriteOptions)(implicit mat: Materializer): Future[List[Path]]
}

object UploadDao {

  val DefaultWriteOptions: Set[OpenOption] = Set(CREATE, WRITE, TRUNCATE_EXISTING, SYNC)

  def apply(dir: Path) = new FileUploadDao(dir)

  class FileUploadDao(val dir: Path) extends UploadDao with LowPriorityIOImplicits with StrictLogging {
    require(dir.isDir, s"$dir is not a directory")

    override def toString = s"FileUploadDao($dir)"

    def writeDown(inputFiles: List[Upload], options: Set[OpenOption] = UploadDao.DefaultWriteOptions)(implicit mat: Materializer): Future[List[Path]] = {
      import mat._

      /**
        * write down the multipart input(s)
        */
      val futures: List[Future[Path]] = inputFiles.map {
        case Upload(name, src, _) =>
          val dest = if (name.asPath.isAbsolute) {
            name.asPath
          } else {
            dir.resolve(name)
          }

          if (!dest.exists) {
            val writeFut = src.runWith(FileIO.toPath(dest, options))
            writeFut.onComplete {
              case res => logger.debug(s"Writing to $dest completed w/ $res")
            }
            writeFut.map(_ => dest)
          } else {
            Future.successful(dest)
          }
      }
      if (futures.isEmpty) {
        Future.successful(Nil)
      } else {
        Future.sequence(futures)
      }
    }
  }

}
