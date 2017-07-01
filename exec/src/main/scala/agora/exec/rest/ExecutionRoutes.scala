package agora.exec.rest

import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.ws.UpgradeToWebSocket
import akka.http.scaladsl.server.Directives.{entity, path, _}
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax._
import agora.api._
import agora.domain.io.implicits._
import agora.exec.ExecConfig
import agora.exec.log.IterableLogger._
import agora.exec.model.{OperationResult, RunProcess, Upload}
import agora.exec.ws.ExecuteOverWS
import agora.rest.worker.WorkerRoutes

import scala.concurrent.Future

object ExecutionRoutes {
  def apply(execConfig: ExecConfig) = new ExecutionRoutes(execConfig)
}

/**
  * Combines both the worker routes and some job output ones.
  *
  * NOTE: These routes are separate from the WorkerRoutes which handle
  * jobs that have been redirected from the exchange
  *
  * @param execConfig
  */
class ExecutionRoutes(val execConfig: ExecConfig) extends StrictLogging with FailFastCirceSupport {

  import execConfig._

  def routes(workerRoutes: WorkerRoutes, exchangeRoutes: Option[Route]): Route = {
    execConfig.routes(workerRoutes, exchangeRoutes) ~ jobRoutes
  }

  def jobRoutes = pathPrefix("rest" / "exec") {
    listJobs ~ removeJob ~ jobOutput ~ submitJobFromForm ~ runSavedJob ~ search ~ listMetadata ~ getJob
  }

  /**
    * Simply upload a job (RunProc), but don't execute it -- return an 'id' for it to be run.
    *
    * This is useful for the UI which makes one multipart request to upload/save a job, and
    * another to then read the output via web sockets
    */
  def submitJobFromForm: Route = post {
    path("submit") {
      extractRequestContext { reqCtxt =>
        import reqCtxt.{executionContext, materializer}

        entity(as[Multipart.FormData]) { (formData: Multipart.FormData) =>
          val jobId = nextJobId()

          def uploadDir = execConfig.uploads.dir(jobId).get

          val uploadFutures: Future[(RunProcess, List[Upload])] = {
            MultipartExtractor.fromUserForm(execConfig.uploads, formData, uploadDir, execConfig.chunkSize)
          }
          val savedIdFuture = uploadFutures.map {
            case (runProcess, uploads) =>
              execConfig.execDao.save(jobId, runProcess, uploads)
              Json.obj("jobId" -> Json.fromString(jobId))
          }

          complete {
            savedIdFuture
          }
        }
      }
    }
  }

  def runSavedJob: Route = get {
    (path("run") & pathEnd) {
      extractRequestContext { requestCtxt =>
        import requestCtxt.materializer

        requestCtxt.request.header[UpgradeToWebSocket] match {
          case None => complete(HttpResponse(400, entity = "Not a valid websocket request"))
          case Some(upgrade) =>
            logger.info(s"upgrading to web socket")

            complete {
              upgrade.handleMessages(ExecuteOverWS(execConfig))
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

  def listMetadata: Route = {
    get {
      (path("metadata") & pathEnd) {
        complete {
          import serverImplicits._
          execDao.listMetadata.map { map =>
            import io.circe.syntax._

            map.asJson
          }
        }
      }
    }
  }

  def search: Route = {
    get {
      (path("search") & pathEnd) {
        parameterMap { allParams =>
          complete {
            import serverImplicits._
            execDao.findJobsByMetadata(allParams).map { ids =>
              import io.circe.generic.auto._

              ids.asJson
            }
          }
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

  /**
    * remove the output for a job
    */
  def getJob = {
    get {
      (path("job") & parameters('id.as[String])) { jobId =>
        extractRequestContext { ctxt =>
          import ctxt.materializer
          import ctxt.executionContext

          complete {
            execDao.get(jobId).flatMap {
              case (runProcess, uploads) =>
                val j = runProcess.asJson

                if (uploads.isEmpty) {
                  val respJson = Json.obj("runProcess" -> j, "uploadCount" -> Json.fromInt(uploads.size))
                  Future.successful(respJson)
                } else {
                  val futures: List[Future[(String, Json)]] = uploads.map { upload =>
                    upload.source.reduce(_ ++ _).runWith(Sink.head).map { bytes =>
                      upload.name -> Json.fromString(bytes.utf8String)
                    }
                  }
                  Future.sequence(futures).map { pairs =>
                    val jsonMap = List("runProcess" -> j, "uploadCount" -> Json.fromInt(uploads.size)) ++ pairs
                    Json.obj(jsonMap: _*)
                  }
                }
            }
          }
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
    val uploadCount  = jobUploadDir.children.size
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
