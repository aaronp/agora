package jabroni.exec.dao

import java.nio.file.{Path, Paths, StandardOpenOption}

import akka.stream.Materializer
import akka.stream.scaladsl.FileIO
import com.typesafe.scalalogging.StrictLogging
import jabroni.domain.io.LowPriorityIOImplicits
import jabroni.exec.model.Upload

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Properties

trait UploadDao {
  def writeDown(inputFiles: List[Upload]): Future[List[Path]]
}

object UploadDao {

  def apply(dir: Path = Paths.get(Properties.userDir))(implicit mat: Materializer) = new FileUploadDao(dir)

  class FileUploadDao(dir: Path)(implicit mat: Materializer) extends UploadDao with LowPriorityIOImplicits with StrictLogging {

    import mat._

    def writeDown(inputFiles: List[Upload]): Future[List[Path]] = {

      /**
        * write down the multipart input(s)
        */
      val futures: List[Future[Path]] = inputFiles.map {
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
