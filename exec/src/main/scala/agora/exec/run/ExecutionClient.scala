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
  * A client of the [[agora.exec.rest.ExecutionRoutes]] and [[agora.exec.rest.UploadRoutes]]
  *
  * @param client
  */
case class ExecutionClient(override val client: RestClient, defaultFrameLength: Int, allowTruncation: Boolean)(implicit uploadTimeout: FiniteDuration)
    extends UploadClient
    with AutoCloseable {

  /**
    * Execute the request
    * @param proc
    * @return the http response whose entity body contains the process output
    */
  def execute(proc: RunProcess): Future[HttpResponse] = {
    import client.executionContext
    client.send(ExecutionClient.asRequest(proc))
  }

  /**
    * like execute, but returns a user-friendly return value
    * @param proc the process to run
    * @return the future of the process output
    */
  def run(proc: RunProcess): Future[Iterator[String]] = {
    import client.materializer
    import client.executionContext
    execute(proc).map { httpResp =>
      val iter: Iterator[String] = IterableSubscriber.iterate(httpResp.entity.dataBytes, proc.frameLength.getOrElse(defaultFrameLength), allowTruncation)
      proc.filterForErrors(iter)
    }
  }

  final def run(cmd: String, theRest: String*): Future[Iterator[String]] = run(RunProcess(cmd :: theRest.toList))

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
