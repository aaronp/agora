package agora.exec.workspace

import java.nio.file.StandardOpenOption._
import java.nio.file.{OpenOption, Path, StandardCopyOption}

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
}

object UploadDao {

  val DefaultWriteOptions: Set[OpenOption] = Set(CREATE, WRITE, TRUNCATE_EXISTING, SYNC)

  def apply(dir: Path) = new FileUploadDao(dir)

  class FileUploadDao(val dir: Path) extends UploadDao with LowPriorityIOImplicits with StrictLogging {
    require(dir.isDir, s"$dir is not a directory")

    override def toString = s"FileUploadDao($dir)"

    def writeDownUpload(upload: Upload, options: Set[OpenOption])(implicit mat: Materializer): Future[(Long, Path)] = {
      import mat._
      val Upload(targetFileName, src, _) = upload

      val targetFilePath = if (targetFileName.asPath.isAbsolute) {
        targetFileName.asPath
      } else {
        dir.resolve(targetFileName)
      }

      MetadataFile.newPartialUploadFileForUpload(targetFilePath) match {
        case None => Future.failed(new Exception(s"Couldn't create an upload file for $targetFileName under $dir"))
        case Some(tmpUploadDest) =>
          val writeFut: Future[IOResult] = src.runWith(FileIO.toPath(tmpUploadDest, options))

          writeFut.map { ioResult: IOResult =>
            logger.debug(s"Moving $tmpUploadDest to $targetFilePath")
            val size = ioResult.count

            tmpUploadDest.moveTo(targetFilePath, StandardCopyOption.ATOMIC_MOVE)
            val pear = (size, targetFilePath)
            logger.debug(s"Write future $targetFilePath mapped to $pear")
            pear
          }
      }
    }

  }

}
