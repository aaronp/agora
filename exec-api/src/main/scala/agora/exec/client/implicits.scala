package agora.exec.client

import agora.api.exchange.{AsClient, Dispatch}
import agora.exec.model.{RunProcess, RunProcessResult}
import agora.exec.{ExecApiConfig, client}
import agora.rest.RestConversionImplicits

object implicits extends ExecConversionImplicits

trait ExecConversionImplicits extends RestConversionImplicits {

  implicit def asClient(implicit config: ExecApiConfig): AsClient[RunProcess, RunProcessResult] = {
    new client.ExecConversionImplicits.ExecAsClient(config)
  }
}

object ExecConversionImplicits {

  class ExecAsClient(config: ExecApiConfig) extends AsClient[RunProcess, RunProcessResult] {
    override def dispatch(dispatch: Dispatch[RunProcess]) = {
      val rest   = config.clientConfig.clientFor(dispatch.matchedWorker.location)
      val client = ExecutionClient(rest, config.defaultFrameLength)(config.uploadTimeout)
      client.run(dispatch.request)
    }
  }
}
