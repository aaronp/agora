package agora.exec.client

import agora.api.exchange.{Dispatch, AsClient}
import agora.exec.ExecApiConfig
import agora.exec.model.{RunProcess, RunProcessResult}
import agora.rest.{ClientConfig, RestConversionImplicits}

object implicits extends ExecConversionImplicits

trait ExecConversionImplicits extends RestConversionImplicits {

  //  override def run(input: RunProcess) = {
  //    input.output.streaming match {
  //      case None    => runAndSave(input)
  //      case Some(_) => runAndSelect(input).flatMap(_.result)
  //    }
  //  }
  type ConfAndDefaultFrameLen = (ClientConfig, Int)

  implicit def asDispatchable(implicit config: ExecApiConfig): AsClient[RunProcess, RunProcessResult] = {

    new AsClient[RunProcess, RunProcessResult] {
      override def dispatch(dispatch: Dispatch[RunProcess]) = {
        val rest                       = config.clientConfig.clientFor(dispatch.matchedWorker.location)
        val client                     = ExecutionClient(rest, config.defaultFrameLength)(config.uploadTimeout)
        client.run(dispatch.request)
      }
    }
  }
}
