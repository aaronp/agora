package agora

import agora.api.worker.HostLocation
import akka.http.scaladsl.model.{HttpHeader, Uri}
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}
import _root_.io.circe.Json

package object rest {

  object implicits {

    implicit class RichKey(val key: String) extends AnyVal {
      def asHeader(value: String): HttpHeader = {
        HttpHeader.parse(key, value) match {
          case ParsingResult.Ok(h, Nil) => h
          case res =>
            val msg = s"Couldn't create header '$key'=>$value<  -->\n" + res.errors.mkString(";")
            throw new Exception(msg)
        }
      }
    }

  }

  def configForArgs(args: Array[String], fallback: Config = ConfigFactory.empty): Config = {
    import agora.domain.RichConfig.implicits._
    fallback.withUserArgs(args)
  }

  def asJson(c: Config): Json = {
    val json = c.root.render(ConfigRenderOptions.concise().setJson(true))
    _root_.io.circe.parser.parse(json).right.get
  }

  def asHostLocation(conf: Config) = {
    HostLocation(host = conf.getString("host"), port = conf.getInt("port"))
  }

  def asLocation(uri: Uri): HostLocation = {
    val addresses = uri.authority.host.inetAddresses.toList
    val hostName  = addresses.head.getHostName
    HostLocation(hostName, uri.authority.port)
  }
}
