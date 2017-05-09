package jabroni

import akka.http.scaladsl.model.HttpHeader
import akka.http.scaladsl.model.HttpHeader.ParsingResult
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.typesafe.config.{Config, ConfigRenderOptions}
import io.circe.Json
import io.circe.parser.parse
import jabroni.api.worker.HostLocation
import rx.core.Propagator.ExecContext

import scala.concurrent.Future

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

  def srcAsText(src: Source[ByteString, Any])(implicit materializer : akka.stream.Materializer): Future[String] = {
    import materializer._
    src.runReduce(_ ++ _).map(bytes => bytes.decodeString("UTF-8"))
  }

  def asHostLocation(conf: Config) = {
    HostLocation(host = conf.getString("host"), port = conf.getInt("port"))
  }
}
