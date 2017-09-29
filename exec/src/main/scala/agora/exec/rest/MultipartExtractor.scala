package agora.exec
package rest

import java.nio.file.{Path, StandardOpenOption}

import agora.exec.model.Upload
import agora.rest.multipart.MultipartInfo
import akka.http.scaladsl.model.Multipart
import akka.stream.Materializer
import akka.stream.scaladsl.FileIO
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future

/**
  * One place to stick all the ugly parsing of multipart form submssions from web forms
  */
object MultipartExtractor extends LazyLogging {

  /**
    * parse the given form data, writing uploads to the 'saveUnderDir'
    *
    * @param formData
    * @param saveUnderDir
    * @param chunkSize
    * @param mat
    * @return the RunProcess command to run and any uploads
    */
  def apply(formData: Multipart.FormData, saveUnderDir: => Path, chunkSize: Int)(
      implicit mat: Materializer): Future[List[Upload]] = {

    import mat.executionContext
    import agora.rest.multipart.MultipartFormImplicits._

    val nestedUploads: Future[List[Future[Upload]]] = formData.mapMultipart {
      case (MultipartInfo(_, Some(fileName), _), src) =>
        val dest       = saveUnderDir.resolve(fileName)
        val saveFuture = src.runWith(FileIO.toPath(dest, Set(StandardOpenOption.CREATE, StandardOpenOption.WRITE)))
        saveFuture.map { result =>
          require(result.wasSuccessful, s"Couldn't save $fileName to $dest")
          Upload(dest.toAbsolutePath.toString, FileIO.fromPath(dest, chunkSize), Option(result.count))
        }
    }

    nestedUploads.flatMap { list =>
      Future.sequence(list)
    }
  }
}
