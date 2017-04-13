package jabroni.rest.test

import java.io.Closeable

import jabroni.rest.client.{Client, ClientConfig}
import org.scalatest.Matchers
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}

case class ClientTestState(config: Option[ClientConfig] = None,
                           clientOpt: Option[Client] = None)
  extends Matchers
    with ScalaFutures
    with AutoCloseable {

  def connect() = {
    close()
    ClientTestState(clientOpt = Option(Client(config.get)))
  }

  def client = clientOpt.get

  override def close() = {
    clientOpt.foreach {
      case c: Closeable => c.close()
      case _ =>
    }
  }

  implicit override val patienceConfig =
    PatienceConfig(timeout = scaled(Span(5, Seconds)), interval = scaled(Span(150, Millis)))
}
