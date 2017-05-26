package jabroni.exec

import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{path, _}
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax._
import jabroni.api.JobId
import jabroni.domain.io.implicits._
import jabroni.exec.log.IterableLogger._


object ExecutionRoutes {
  def apply(execConfig: ExecConfig, handler: ExecutionHandler) = new ExecutionRoutes(execConfig, handler)
}

/**
  * Combines both the worker routes and some job output ones
  *
  * @param execConfig
  */
class ExecutionRoutes(val execConfig: ExecConfig, handler: ExecutionHandler) extends StrictLogging with FailFastCirceSupport {

  import execConfig._

  def routes: Route = {
    execConfig.routes ~ jobRoutes
  }

  def jobRoutes = pathPrefix("rest" / "exec") {
    listJobs ~ removeJob ~ jobOutput ~ runJobDirect
  }

  /**
    * @TODO - remove this ... it's just for debugging the UI. This route
    * should be the same as the worker route used by the exchange
    */
  def runJobDirect: Route = post {
    path("run") {
      entity(as[Multipart.FormData]) { (formData: Multipart.FormData) =>

        import jabroni.rest.multipart.MultipartFormImplicits._

        extractMaterializer { implicit mat =>

          complete {
            formData.mapMultipart {
              case (info, _) =>
                import io.circe.generic.auto._
                Map("field" -> info.fieldName, "file" -> info.fileName.getOrElse("")).asJson
            }
          }
        }
      }
    }
  }

  def listJobs: Route = {
    get {
      (path("jobs") & pathEnd) {
        complete {
          val ids = logs.path.fold(List[Json]()) { dir =>
            dir.children.toList.sortBy(_.created.toEpochMilli).map { child =>
              Json.obj("id" -> Json.fromString(child.fileName), "started" -> Json.fromString(child.createdString))
            }
          }
          Json.arr(ids: _*)
        }
      }
    }
  }

  /**
    * remove the output for a job
    */
  def removeJob = {
    delete {
      (path("job") & parameters('id.as[String])) { jobId =>
        complete {
          val json = onRemoveJob(jobId).asJson.noSpaces
          HttpResponse(entity = HttpEntity(`application/json`, json))
        }
      }
    }
  }

  private def onRemoveJob(jobId: JobId): OperationResult = {
    val logMsg = pathForJob("logs", logs.path, jobId, None) match {
      case Left(err) => err
      case Right(logDir) =>
        logDir.delete()
        s"Removed ${logDir.toAbsolutePath.toString}"
    }
    val jobUploadDir = uploads.dir(jobId).getOrElse(sys.error("upload dir can't be empty"))
    val uploadCount = jobUploadDir.children.size
    jobUploadDir.delete()
    val uploadMsg = s"Removed ${uploadCount} uploads"
    OperationResult(logMsg, uploadMsg)
  }

  /**
    * Get the output for a job
    */
  def jobOutput = {
    get {
      (path("job") & parameters('id.as[String], 'file.?)) { (jobId, fileName) =>
        complete {
          onJobOutput(jobId, fileName.getOrElse("std.out"))
        }
      }
    }
  }

  private def onJobOutput(jobId: JobId, logFileName: String): HttpResponse = {
    pathForJob("logs", logs.path, jobId, Option(logFileName)) match {
      case Left(err) =>
        HttpResponse(NotFound, entity = HttpEntity(`application/json`, OperationResult(err).asJson.noSpaces))
      case Right(logFile) =>
        val src = Source.fromIterator(() => logFile.lines).map(s => ByteString(s"$s\n"))
        HttpResponse(entity = HttpEntity(contentType = `text/plain(UTF-8)`, src))
    }
  }


  override def toString = {
    s"ExecutionRoutes {${execConfig.describe}}"
  }

}
