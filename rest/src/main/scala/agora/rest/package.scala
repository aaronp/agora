package agora

import agora.api.worker.HostLocation
import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}

package object rest {

  object implicits {

    implicit class RichKey(val key: String) extends AnyVal {
      def asHeader(value: String): HttpHeader = {
        HttpHeader.parse(key, value) match {
          case ParsingResult.Ok(h, Nil) => h
          case res                      => throw new Exception(res.errors.mkString(";"))
        }
      }
    }

  }

  def configForArgs(args: Array[String], fallback: Config = ConfigFactory.empty): Config = {
    import agora.domain.RichConfig.implicits._
    fallback.withUserArgs(args)
  }

  def asJson(c: Config) = {
    val json = c.root.render(ConfigRenderOptions.concise().setJson(true))
    _root_.io.circe.parser.parse(json).right.get
  }

  def asHostLocation(conf: Config) = {
    HostLocation(host = conf.getString("host"), port = conf.getInt("port"))
  }
}
