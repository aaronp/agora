package jabroni.exec

import java.nio.file.Path

import log._
import log.ProcessLoggers._
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
import jabroni.domain.io.Sources
import jabroni.domain.io.implicits._
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

  val workerRoutes = workerConfig.workerRoutes
  workerRoutes.addMultipartHandler { (req: WorkContext[MultipartPieces]) =>
    val runner = newRunner(req)
    req.completeWith(onRun(runner, req, uploadTimeout))
  }

  def routes: Route = {
    workerRoutes.routes ~ listJobs ~ removeJob ~ jobOutput
  }

  def start(): Future[RunningService[WorkerConfig, WorkerRoutes]] = {
    workerConfig.runWithRoutes("Exec", routes, workerRoutes)
  }


  def listJobs: Route = {
    get {
      (path("jobs") & pathEnd) {
        complete {
          val ids = baseLogDir.fold(List[Json]()) { dir =>
            dir.children.map { child =>
              Json.obj("id" -> Json.fromString(child.fileName), "started" -> Json.fromString(child.createdString))
            }.toList
          }
          Json.arr(ids: _*)
        }
      }
    }
  }

  /**
    * remove the output for a job
    */
  def removeJob = { //(baseLogDir: Option[Path], baseUploadDir: Path) = {
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
    val logMsg = pathForJob(baseLogDir, jobId, None) match {
      case Left(err) => err
      case Right(logDir) =>
        logDir.delete()
        s"Removed ${logDir.toAbsolutePath.toString}"
    }
    val jobUploadDir = baseUploadDir.resolve(jobId)

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
      (path("job") & parameters('id.as[String], 'file.as[String])) { (jobId, fileName) =>
        complete {
          onJobOutput(jobId, fileName)
        }
      }
    }
  }

  private def onJobOutput(jobId: JobId, logFileName: String): HttpResponse = {
    pathForJob(baseLogDir, jobId, Option(logFileName)) match {
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

      val src = Source.fromIterator(() => run)
      val bytes = src.map(line => ByteString(s"$line\n"))

      HttpResponse(entity = HttpEntity(outputContentType, bytes))
    }
  }
}
