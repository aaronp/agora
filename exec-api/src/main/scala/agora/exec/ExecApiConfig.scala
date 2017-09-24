package agora.exec

import java.util.concurrent.TimeUnit

import agora.rest.ClientConfig
import com.typesafe.config.Config

import scala.concurrent.duration._

trait ExecApiConfig {
  def execConfig: Config

  def defaultFrameLength = execConfig.getInt("defaultFrameLength")

  implicit def uploadTimeout: FiniteDuration = execConfig.getDuration("uploadTimeout", TimeUnit.MILLISECONDS).millis

  def clientConfig: ClientConfig

}