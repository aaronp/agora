package agora.exec.client

import agora.api.Implicits._
import agora.api.exchange.{Exchange, SubmissionDetails}
import agora.exec.ExecApiConfig
import agora.exec.model.{RunProcess, RunProcessResult}
import akka.stream.Materializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto._

/**
  * An facade for ProcessRunner which will submit [[RunProcess]] jobs
  * via an exchange
  *
  * @param exchange
  * @param execApiConfig
  * @param defaultDetails
  * @param mat
  */
case class RemoteRunner(exchange: Exchange,
                        execApiConfig: ExecApiConfig,
                        defaultDetails: SubmissionDetails = SubmissionDetails())(implicit mat: Materializer)
    extends ProcessRunner
    with ExecConversionImplicits
    with FailFastCirceSupport {

  import mat._

  private implicit def confAndFrame = execApiConfig.clientConfig

  override def run(proc: RunProcess) = {
    val details = RemoteRunner.prepare(proc, defaultDetails)
    proc.asJob(details).enqueueIn[RunProcessResult](exchange)
  }
}

object RemoteRunner {

  /**
    * sets up additional matching criteria on the rest endpoint and a workspace
    *
    * @param defaultDetails
    * @param proc
    * @return
    */
  def prepare(proc: RunProcess, defaultDetails: SubmissionDetails = SubmissionDetails()): SubmissionDetails = {
    val base = defaultDetails.matchingPath("rest/exec/run")
    if (proc.hasDependencies) {
      base.andMatching("workspaces" includes proc.workspace)
    } else {
      base
    }
  }
}
