package jabroni.rest.test

import java.io.Closeable

import jabroni.api.Ledger
import jabroni.api.Ledger.InMemoryLedger
import jabroni.rest.server.{RestService, ServerConfig}
import org.scalatest.Matchers
import org.scalatest.concurrent.ScalaFutures

case class ServerTestState(serverConfig: Option[ServerConfig] = None,
                           server: Option[RestService.RunningService] = None,
                           ledger: InMemoryLedger = new InMemoryLedger)
  extends Matchers
    with ScalaFutures
    with Closeable {

  def startServer() = {
    close()
    val conf = serverConfig.get
    import conf.implicits.executionContext
    ServerTestState(server = Option(RestService.start(conf, Ledger.logging(ledger)).futureValue))
  }

  def clearLedger() = {
    ledger.clear()
  }

  override def close() = {
    server.foreach(_.stop)
  }
}
