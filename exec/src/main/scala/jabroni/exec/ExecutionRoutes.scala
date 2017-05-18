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
import jabroni.domain.TryIterator
import jabroni.domain.io.Sources
import jabroni.domain.io.implicits._
import jabroni.exec.log.IterableLogger._
import jabroni.rest.RunningService
import jabroni.rest.multipart.{MultipartInfo, MultipartPieces}
import jabroni.rest.worker.{WorkContext, WorkerConfig, WorkerRoutes}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/**
  * Combines both the worker routes and some job output onesExec§§§§
  *
  * @param execConfig
  */
case class ExecutionRoutes(execConfig: ExecConfig) extends StrictLogging with FailFastCirceSupport {

  import execConfig._

  workerRoutes.addMultipartHandler { (req: WorkContext[MultipartPieces]) =>
    val runner = newRunner(req, false)
    import execConfig.implicits._
    val handlerFuture = onRun(runner, req, uploadTimeout).recover {
      case pr: ProcessException =>
        val errorEntity = HttpEntity(`application/json`, pr.json.noSpaces)
        HttpResponse(status = InternalServerError, entity = errorEntity)
      case other =>
        throw other
    }
    req.completeWith(handlerFuture)
  }

  def routes: Route = {
    execConfig.routes ~ jobRoutes
  }

  def jobRoutes = pathPrefix("rest") {
    listJobs ~ removeJob ~ jobOutput
  }

  def start(): Future[RunningService[WorkerConfig, WorkerRoutes]] = {
    runWithRoutes("Exec", routes, workerRoutes)
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

  def onRun(runner: ProcessRunner,
            req: WorkContext[MultipartPieces],
            uploadTimeout: FiniteDuration,
            outputContentType: ContentType = `text/plain(UTF-8)`): Future[HttpResponse] = {
    import req.requestContext._

    val uploadFutures = req.request.collect {
      case (MultipartInfo(_, Some(fileName), _), src) =>
        Sources.sizeOf(src).map { len =>
          Upload(fileName, len, src)
        }
    }
    val runProcFuture: Future[RunProcess] = req.multipartJson[RunProcess]
    for {
      runProc <- runProcFuture
      uploads <- Future.sequence(uploadFutures.toList)
    } yield {
      def run = Await.result(runner.run(runProc, uploads), uploadTimeout)

      val bytes = Source.fromIterator(() => run).map(line => ByteString(s"$line\n"))
      HttpResponse(entity = HttpEntity(outputContentType, bytes))
    }
  }
}
