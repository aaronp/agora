package agora.exec.client

import agora.api.Implicits._
import agora.api.exchange._
import agora.exec.model.{RunProcess, RunProcessResult}
import agora.exec.{ExecApiConfig, WorkspacesKey}
import akka.stream.Materializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.auto._

/**
  * An facade for ProcessRunner which will submit [[RunProcess]] jobs
  * via an exchange. The runner may specify matching criteria such as the existence of
  * a workspace or a health value under a given threshold.
  *
  * Subclasses may override the [[submissionDetailsForJob]] method to provide their own match criteria.
  *
  * @param exchange
  * @param execApiConfig
  * @param defaultDetails
  * @param mat
  */
class RemoteRunner(val exchange: Exchange, val defaultDetails: SubmissionDetails = SubmissionDetails())(
    implicit execApiConfig: ExecApiConfig,
    mat: Materializer)
    extends ProcessRunner
    with ExecConversionImplicits
    with FailFastCirceSupport {

  import mat._

  /**
    * with an implicit client config in scope, the [[agora.rest.RestConversionImplicits]]
    * are able to produce an 'AsClient' from the exchange's response
    *
    * @return the client config
    */
  private implicit def clientConfig = execApiConfig.clientConfig

  def submissionDetailsForJob(job: RunProcess) = defaultDetails

  override def run(proc: RunProcess) = {
    val submissionDetails = submissionDetailsForJob(proc)
    implicit val details  = RemoteRunner.prepare(proc, submissionDetails)
    exchange.enqueue(proc)
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
      base.andMatching(WorkspacesKey includes proc.workspace).orElseMatch(base.workMatcher)
    } else {
      base
    }
  }
}
