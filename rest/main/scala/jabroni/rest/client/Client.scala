package jabroni.rest.client

import java.io.Closeable

import akka.http.scaladsl.model._
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport

import scala.language.reflectiveCalls

trait Client


object Client {

  def apply(config: ClientConfig): Client = new RemoteClient(config)

  private class RemoteClient(config: ClientConfig) extends Client
    with FailFastCirceSupport
    with StrictLogging
    with Closeable {

    private val endpoint: RestClient = RestClient(config)

    private def mkUri(path: String) = Uri(s"http://${config.host}:${config.port}/rest/$path")

    override def close(): Unit = endpoint.close()
  }

}
