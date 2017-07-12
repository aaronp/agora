package agora.exec
package rest

import java.nio.file.{Path, StandardOpenOption}

import akka.http.scaladsl.model.Multipart
import akka.stream.Materializer
import akka.stream.scaladsl.{FileIO, Sink}
import com.typesafe.scalalogging.LazyLogging
import io.circe.generic.auto._
import io.circe.parser._
import agora.exec.model.{RunProcess, Upload}
import agora.rest.multipart.MultipartInfo

import scala.concurrent.Future

/**
  * One place to stick all the ugly parsing of multipart form submssions from web forms
  */
object MultipartExtractor extends LazyLogging {

  val jsonKey = RunProcess.FormDataKey

  /**
    * parses forms put together from ExecuteForm.ameliorateForm. I really need to change that nonce name.
    *
    * Expects the command text to be under 'command'
    *
    * @param uploadsConfig
    * @param formData
    * @param saveUnderDir
    * @param chunkSize
    * @param mat
    * @return
    */
  def fromUserForm(uploadsConfig: PathConfig, formData: Multipart.FormData, saveUnderDir: => Path, chunkSize: Int)(implicit mat: Materializer): Future[(RunProcess, List[Upload])] = {
    import agora.rest.multipart.MultipartFormImplicits._

    import mat._

    val futureOfEithers: Future[List[Future[Either[Map[String, String], Upload]]]] = formData.mapMultipart {
      case (MultipartInfo(name, None, _), src) =>
        src.map(_.utf8String).runWith(Sink.head).map { value =>
          Left(Map(name -> value))
        }
      case (MultipartInfo(_, Some(fileName), contentType), src) =>
        val dest       = saveUnderDir.resolve(fileName)
        val saveFuture = src.runWith(FileIO.toPath(dest, Set(StandardOpenOption.CREATE, StandardOpenOption.WRITE)))
        saveFuture.map { result =>
          require(result.wasSuccessful, s"Couldn't save $fileName to $dest")
          val upload = Upload(dest.toAbsolutePath.toString, result.count, FileIO.fromPath(dest, chunkSize), contentType)
          Right[Map[String, String], Upload](upload)
        }
    }

    futureOfEithers.flatMap { list =>
      Future.sequence(list).map { eithers =>
        val parts = eithers.partition(_.isLeft)
        parts match {
          case (maps, uploadRights) =>
            val mapOfFormFields = if (maps.isEmpty) {
              sys.error("no non-file upload parts found")
            } else {
              maps
                .collect {
                  case Left(map) => map
                }
                .reduce(_ ++ _)
            }
            val uploads = uploadRights.map {
              case Right(u) => u
              case left     => sys.error(s"partition is broken: $left")
            }
            val codes = mapOfFormFields.get("successExitCodes").map(_.split("\\s+", -1).map(_.toInt).toSet).getOrElse(Set(0))

            val runProcess = new RunProcess(
              command = mapOfFormFields("command").split("\\s+", -1).toList,
              env = Map.empty,
              frameLength = mapOfFormFields.get("frameLength").map(_.toInt),
              successExitCodes = codes,
              errorMarker = mapOfFormFields.get("errorMarker").getOrElse(RunProcess.DefaultErrorMarker)
            )
            runProcess -> uploads
          case invalid =>
            sys.error(s"expected a single run process json w/ key '$jsonKey' and some file uploads, but got: $invalid")
        }
      }
    }
  }

  /**
    * parse the given form data, writing uploads to the 'saveUnderDir'
    *
    * @param formData
    * @param saveUnderDir
    * @param chunkSize
    * @param mat
    * @return the RunProcess command to run and any uploads
    */
  def apply(formData: Multipart.FormData, saveUnderDir: => Path, chunkSize: Int)(implicit mat: Materializer): Future[(Option[RunProcess], List[Upload])] = {

    import agora.rest.multipart.MultipartFormImplicits._
    import mat._

    val futureOfEithers: Future[List[Future[Either[RunProcess, Upload]]]] = formData.mapMultipart {
      case (MultipartInfo(`jsonKey`, None, _), src) =>
        val runProcessSource = src.reduce(_ ++ _).map(_.utf8String).map { text =>
          val unpickled = decode[RunProcess](text)
          unpickled match {
            case Left(err)   => throw new Exception(s"Error parsing part '$jsonKey' as json >>${text}<< : $err", err)
            case Right(json) => Left[RunProcess, Upload](json)
          }
        }
        runProcessSource.runWith(Sink.head)
      case (MultipartInfo(_, Some(fileName), contentType), src) =>
        val dest       = saveUnderDir.resolve(fileName)
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
          case (runProcessLefts, uploadRights) =>
            val rpOpt = runProcessLefts match {
              case Nil                    => None
              case List(Left(runProcess)) => Option(runProcess)
              case invalid                => sys.error(s"expected a single run process json w/ key '$jsonKey' and some file uploads, but got: $invalid")
            }
            val uploads = uploadRights.map {
              case Right(u) => u
              case left     => sys.error(s"partition is broken: $left")
            }
            rpOpt -> uploads
        }
      }
    }
  }
}
