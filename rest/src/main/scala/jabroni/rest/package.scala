package jabroni

import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import com.typesafe.config.{Config, ConfigRenderOptions}
import io.circe.parser.parse

package object rest {


  object implicits {
    implicit class RichKey(val key: String) extends AnyVal {
      def asHeader(value: String): HttpHeader = {
        HttpHeader.parse(key, value) match {
          case ParsingResult.Ok(h, Nil) => h
          case res => throw new Exception(res.errors.mkString(";"))
        }
      }
    }
  }


  def configForArgs(args: Array[String], defaultConfig: Config) = {
    import jabroni.domain.RichConfig.implicits._
    args.asConfig().withFallback(defaultConfig).resolve()
  }


  def asJson(c: Config) = {
    val json = c.root.render(ConfigRenderOptions.concise().setJson(true))
    parse(json).right.get
  }
}
