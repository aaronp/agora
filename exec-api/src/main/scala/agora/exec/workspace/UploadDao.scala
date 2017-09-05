package agora.exec.workspace

import java.nio.file.StandardOpenOption._
import java.nio.file.{OpenOption, Path, Paths}

import agora.api.io.LowPriorityIOImplicits
import agora.exec.model.Upload
import akka.stream.Materializer
import akka.stream.scaladsl.FileIO
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.Future
import scala.util.Properties

trait UploadDao {
  def dir: Path

  def writeDown(inputFiles: List[Upload], options: Set[OpenOption] = UploadDao.DefaultWriteOptions)(implicit mat: Materializer): Future[List[Path]]

  def read(implicit mat: Materializer): Future[List[Upload]]
}

object UploadDao {

  val DefaultWriteOptions: Set[OpenOption] = Set(CREATE, WRITE, TRUNCATE_EXISTING, SYNC)

  def apply(dir: Path = Paths.get(Properties.userDir)) = new FileUploadDao(dir)

  class FileUploadDao(override val dir: Path) extends UploadDao with LowPriorityIOImplicits with StrictLogging {
    require(dir.isDir, s"$dir is not a directory")

    override def toString = s"FileUploadDao($dir)"

    override def read(implicit mat: Materializer): Future[List[Upload]] = {

      val uploads = dir.children.map { uploadPath =>
        val src = FileIO.fromPath(uploadPath)
        Upload(uploadPath.getFileName.toString, src, Option(uploadPath.size))
      }.toList
      Future.successful(uploads)
    }

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
