package agora.exec.session

import java.nio.file.Path

import agora.exec.ExecutionHandler
import agora.exec.model.{RunProcess, Upload}
import agora.exec.rest.MultipartExtractor
import agora.exec.run.ProcessRunner
import agora.rest.worker.WorkContext
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.ContentTypes.`text/plain(UTF-8)`
import akka.http.scaladsl.model.{ContentType, HttpEntity, HttpResponse, Multipart}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import io.circe.generic.auto._

class SessionHandler(runner: ProcessRunner,
                     sessionDir: Path,
                     chunkSize: Int,
                     timeout: FiniteDuration,
                     //sessionContext: WorkContext[String],
                     outputContentType: ContentType = `text/plain(UTF-8)`)
    extends FailFastCirceSupport {

  import agora.domain.io.implicits._

  lazy val uploadDir = sessionDir.mkDirs()

  /**
    * Execute summat in the session dir
    * @param ctxt
    * @return
    */
  def onWork(ctxt: WorkContext[RunProcess]) = {
    import ctxt.requestContext._

    val uploads = Nil
    val bytes   = ExecutionHandler.asByteIterator(runner, timeout, ctxt.request, uploads)

    val chunked: HttpEntity.Chunked  = HttpEntity(outputContentType, bytes)
    val future: Future[HttpResponse] = Marshal(chunked).toResponseFor(ctxt.requestContext.request)

    ctxt.completeWith(future)
  }

  def onUpload(ctxt: WorkContext[Multipart.FormData]) = {
    import ctxt.requestContext._

    val exp: Future[(Option[RunProcess], List[Upload])] = MultipartExtractor(ctxt.request, uploadDir, chunkSize)
    val sessionData: Future[WorkContext[Multipart.FormData]] = exp.flatMap {
      case (opt, uploads) =>
        val future = ctxt.updateSubscription { subscription =>
          val Right(oldData) = subscription.details.get[SessionState]("session")
          val newData        = oldData.add(uploads.map(_.name))
          subscription.append("session", newData)
        }
        future
    }

    val resp = sessionData.map(_ => Json.fromBoolean(true))
    ctxt.completeWith(resp)
  }
}
