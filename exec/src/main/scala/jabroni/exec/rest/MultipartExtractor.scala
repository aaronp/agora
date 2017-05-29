package jabroni.exec
package rest

import java.nio.file.StandardOpenOption

import akka.http.scaladsl.model.Multipart
import akka.stream.scaladsl.{FileIO, Sink}
import io.circe.generic.auto._
import io.circe.parser._
import jabroni.api.JobId
import jabroni.exec.RunProcess
import jabroni.rest.multipart.MultipartInfo
import jabroni.rest.worker.WorkContext

import scala.concurrent.Future

object MultipartExtractor {

  val jsonKey = WorkContext.multipartKey[RunProcess]

  def apply(uploadsConfig: PathConfig,
            ctxt: WorkContext[Multipart.FormData],
            jobId: JobId,
            chunkSize: Int): Future[(RunProcess, List[Upload])] = {

    import ctxt.requestContext._
    import jabroni.rest.multipart.MultipartFormImplicits._

    val futureOfEithers: Future[List[Future[Either[RunProcess, Upload]]]] = ctxt.request.mapMultipart {
      case (MultipartInfo(`jsonKey`, None, _), src) =>
        val runProcessSource = src.reduce(_ ++ _).map(_.utf8String).map { text =>

          val unpickled = decode[RunProcess](text)
          unpickled match {
            case Left(err) => throw new Exception(s"Error parsing part '$jsonKey' as json >>${text}<< : $err", err)
            case Right(json) => Left[RunProcess, Upload](json)
          }
        }
        runProcessSource.runWith(Sink.head)
      case (MultipartInfo(_, Some(fileName), contentType), src) =>
        val dest = uploadsConfig.dir(jobId).get.resolve(fileName)
        val saveFuture = src.runWith(FileIO.toPath(dest, Set(StandardOpenOption.CREATE, StandardOpenOption.WRITE)))
        saveFuture.map { result =>
          require(result.wasSuccessful, s"Couldn't save $fileName to $dest")
          val upload = Upload(dest.toAbsolutePath.toString, result.count, FileIO.fromPath(dest, chunkSize), contentType)
          Right[RunProcess, Upload](upload)
        }
    }

    futureOfEithers.flatMap { list =>
      Future.sequence(list).map { eithers =>
        val parts = eithers.partition(_.isLeft)
        parts match {
          case (List(Left(runProcess)), uploadRights) =>
            val uploads = uploadRights.map {
              case Right(u) => u
              case left => sys.error(s"partition is brokenL $left")
            }
            runProcess -> uploads
          case invalid =>
            sys.error(s"expected a single run process json w/ key '$jsonKey' and some file uploads, but got: $invalid")
        }
      }
    }
  }
}
