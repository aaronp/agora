package agora.exec.run

import agora.domain.IterableSubscriber
import agora.exec.model.RunProcess
import agora.rest.client.RestClient
import akka.http.scaladsl.client.RequestBuilding
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{ExecutionContext, Future}
import scala.language.{implicitConversions, reflectiveCalls}

/**
  * A client of the ExecutionRoutes
  *
  * @param client
  */
case class ExecutionClient(client: RestClient, defaultFrameLength: Int, allowTruncation: Boolean)(implicit uploadTimeout: FiniteDuration) extends AutoCloseable {

  def uploader = UploadClient(client)

  def execute(proc: RunProcess) = {
    import client.executionContext
    import client.materializer
    client.send(ExecutionClient.asRequest(proc))
  }
  def run(proc: RunProcess): Future[Iterator[String]] = {
    import client.executionContext
    import client.materializer
    execute(proc).map { httpResp =>
      val iter: Iterator[String] = IterableSubscriber.iterate(httpResp.entity.dataBytes, proc.frameLength.getOrElse(defaultFrameLength), allowTruncation)
      proc.filterForErrors(iter)
    }
  }

  final def run(cmd: String, theRest: String*): Future[Iterator[String]] = {
    run(RunProcess(cmd :: theRest.toList))
  }

  override def close(): Unit = client.close()
}

object ExecutionClient extends RequestBuilding {

  def asRequest(job: RunProcess)(implicit ec: ExecutionContext) = {
    import io.circe.generic.auto._
    import io.circe.syntax._

    val e = HttpEntity(ContentTypes.`application/json`, job.asJson.noSpaces)
    Post("/rest/exec/run").withEntity(e)
  }
}
