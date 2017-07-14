package agora.exec

import agora.api.exchange.MatchObserver
import agora.api.json.JPath
import agora.exec.rest.ExecutionRoutes
import agora.exec.session.{CloseSession, NewSessionHandler, OpenSession}
import agora.rest.exchange.ExchangeRoutes
import agora.rest.worker.WorkerRoutes
import akka.http.scaladsl.model.Multipart
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport

/**
  * Provides functions for setting up the exec service functions
  */
case class ExecSystem(conf: ExecConfig) extends FailFastCirceSupport {

  import conf._

  // either attach to or create a new exchange
  val (exchange, optionalExchangeRoutes) = if (includeExchangeRoutes) {
    val obs                      = MatchObserver()
    val localExchange            = exchangeConfig.newExchange(obs)
    val exRoutes: ExchangeRoutes = exchangeConfig.newExchangeRoutes(localExchange)
    (localExchange, Option(exRoutes.routes))
  } else {
    (exchangeClient, None)
  }

  // create something to actually process jobs
  val handler = ExecutionHandler(conf)

  val sessionHandler = new NewSessionHandler(conf)

  // create a worker to subscribe to the exchange
  lazy val workerRoutes: WorkerRoutes = {
    val wr = newWorkerRoutes(exchange)

    // add the normal handler
    wr.addHandler(handler.onExecute)(subscription, initialRequest, implicitly[FromRequestUnmarshaller[Multipart.FormData]])

    import io.circe.generic.auto._

    // add the session lifecycle handlers. The 'onOpenSession' handler will in turn also create new endpoints
    wr.usingSubscription(OpenSession.prepareSubscription).addHandler(sessionHandler.onOpenSession)
    wr
  }

  val executionRoutes = ExecutionRoutes(conf)

  /** @return routes used to execute
    */
  def routes = {
    executionRoutes.routes(workerRoutes, optionalExchangeRoutes)
  }

}
