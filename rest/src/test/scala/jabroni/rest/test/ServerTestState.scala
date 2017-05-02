package jabroni.rest.test

import java.io.Closeable

import jabroni.api.exchange.Exchange
import jabroni.rest.exchange.ExchangeRoutes
import jabroni.rest.{RestService, ServerConfig}
import org.scalatest.Matchers
import org.scalatest.concurrent.ScalaFutures

case class ServerTestState(serverConfig: Option[ServerConfig] = None,
                           server: Option[RestService.RunningService] = None)
  extends Matchers
    with ScalaFutures
    with Closeable {

  def startExchangeServer() = {
    close()
    val conf = serverConfig.get
    import conf.implicits._

    val route = ExchangeRoutes(onMatch => Exchange(onMatch)).routes
    ServerTestState(server = Option(RestService.start(route, conf).futureValue))
  }


  override def close() = {
    server.foreach(_.stop)
  }
}
