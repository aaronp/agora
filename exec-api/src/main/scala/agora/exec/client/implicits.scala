package agora.exec.client

import agora.api.exchange.{AsClient, Dispatch}
import agora.exec.model.{RunProcess, RunProcessResult}
import agora.exec.{ExecApiConfig, client}
import agora.rest.RestConversionImplicits
import cats.syntax.option._

object implicits extends ExecConversionImplicits

trait ExecConversionImplicits extends RestConversionImplicits {

  implicit def asClient(implicit config: ExecApiConfig): AsClient[RunProcess, RunProcessResult] = {
    new client.ExecConversionImplicits.ExecAsClient(config)
  }
}

object ExecConversionImplicits {

  class ExecAsClient(config: ExecApiConfig) extends AsClient[RunProcess, RunProcessResult] {
    override def dispatch[T <: RunProcess](dispatch: Dispatch[T]) = {
      val rest   = config.clientConfig.clientFor(dispatch.matchedWorker.location)
      val client = ExecutionClient(rest, config.defaultFrameLength, dispatch.matchDetails.some)(config.uploadTimeout)
      client.run(dispatch.request)
    }
  }
}
