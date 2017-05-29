package jabroni.exec.dao

import java.nio.file.{Path, StandardOpenOption}

import akka.stream.scaladsl.FileIO
import com.typesafe.scalalogging.StrictLogging
import jabroni.domain.io.LowPriorityIOImplicits
import jabroni.exec.Upload

import scala.concurrent.Future

trait UploadDao {
  def writeDown(inputFiles: List[Upload]): Future[List[Path]]
}

object UploadDao {

  def apply(dir: Path) = new FileUploadDao(dir)

  class FileUploadDao(dir: Path) extends UploadDao with LowPriorityIOImplicits with StrictLogging {
    def writeDown(inputFiles: List[Upload]): Future[List[Path]] = {

      /**
        * write down the multipart input(s)
        */
      val futures = inputFiles.map {
        case Upload(name, _, src, _) =>
          val dest = name.asPath
          if (!dest.exists) {
            val writeFut = src.runWith(FileIO.toPath(dest, Set(StandardOpenOption.CREATE, StandardOpenOption.WRITE)))
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
