package agora.exec.client

import agora.api.exchange.{AsClient, Exchange, SubmissionDetails, SubmitJob}
import agora.exec.model.{RunProcess, RunProcessResult}
import agora.rest.ClientConfig
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.unmarshalling.FromResponseUnmarshaller
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto._

case class RemoteRunner(exchange: Exchange, clientConfig: ClientConfig, defaultFrameLen: Int) extends ProcessRunner with ExecConversionImplicits with FailFastCirceSupport {

  override def run(proc: RunProcess) = {
    import agora.api.Implicits._
    implicit val confAndFrame = (clientConfig, defaultFrameLen)

    val base = SubmissionDetails().matchingPath("/rest/exec/run")
    val details = if (proc.hasDependencies) {
      base.andMatching("workspaces" includes proc.workspace)
    } else {
      base
    }

    val x = implicitly[FromResponseUnmarshaller[RunProcessResult]]

    implicit def asDispatcher[T: FromResponseUnmarshaller](implicit d: AsClient[SubmitJob, HttpResponse]): AsClient[SubmitJob, T] = {
      ???
    }

    proc.asJob(details).enqueueIn[RunProcessResult](exchange)
  }
}
