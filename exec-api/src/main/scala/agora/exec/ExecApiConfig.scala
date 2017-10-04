package agora.exec

import java.util.concurrent.TimeUnit

import agora.rest.ClientConfig
import com.typesafe.config.Config

import scala.concurrent.duration._

trait ExecApiConfig extends AutoCloseable {
  def config: Config

  def defaultFrameLength = config.getInt("defaultFrameLength")

  implicit def uploadTimeout: FiniteDuration = config.getDuration("uploadTimeout", TimeUnit.MILLISECONDS).millis

  def clientConfig: ClientConfig

}
