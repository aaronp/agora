package jabroni.rest.test

import java.io.Closeable

import jabroni.rest.server.{RestService, ServerConfig}
import org.scalatest.Matchers
import org.scalatest.concurrent.ScalaFutures

case class ServerTestState(serverConfig: Option[ServerConfig] = None,
                           server: Option[RestService.RunningService] = None)
  extends Matchers
    with ScalaFutures
    with Closeable {

  def startServer() = {
    close()
    val conf = serverConfig.get
    ServerTestState(server = Option(RestService.start(conf).futureValue))
  }


  override def close() = {
    server.foreach(_.stop)
  }
}
