package jabroni.rest.test

import jabroni.rest.{ExchangeMain, ServerConfig, WorkerMain}

import scala.concurrent.Future

case class ExchangeTestState(
                              server: Option[ExchangeMain.RunningService] = None,
                              workers: List[WorkerMain.RunningService] = Nil
                            )
  extends ExchangeValidation {

  def startWorker(serverConfig: ServerConfig): ExchangeTestState = {
    stopWorkers(serverConfig)

    import serverConfig.implicits._

    val future = for {
      route <- WorkerMain.routeFromConf(serverConfig)
      running <- WorkerMain.start(route, serverConfig)
    } yield {
      ExchangeTestState(workers = running :: workers)
    }

    future.futureValue
  }


  def startExchangeServer(serverConfig: ServerConfig): ExchangeTestState = {
    closeExchange()
    import serverConfig.implicits._

    val future = for {
      route <- ExchangeMain.routeFromConf(serverConfig)
      running <- ExchangeMain.start(route, serverConfig)
    } yield {
      ExchangeTestState(server = Option(running))
    }

    future.futureValue
  }

  def stopWorkers(serverConfig: ServerConfig): ExchangeTestState = {
    val stopFutures = workers.filter(_.conf.location.port == serverConfig.location.port).map { running =>
      running.stop()
    }
    Future.sequence(stopFutures).futureValue
  }

  def closeExchange() = {
    server.foreach(_.stop)
  }
}
