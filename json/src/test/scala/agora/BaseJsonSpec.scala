package agora

import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

import _root_.io.circe.parser.parse
import scala.concurrent.duration.{FiniteDuration, _}
import scala.concurrent.{Await, Future}

trait BaseJsonSpec extends WordSpec with Matchers with ScalaFutures with BeforeAndAfterAll {

  implicit class JsonHelper(sc: StringContext) {
    // TODO - use io.circe.literal._
    def json(args: Any*) = {
      val text = sc.s(args: _*).stripMargin
      parse(text).right.getOrElse(sys.error(s"Couldn't parse json '$text'"))
    }
  }

  implicit class HoconHelper(sc: StringContext) {
    def hocon(args: Any*) = {
      val jsonString = ConfigFactory
        .parseString(sc.s(args: _*))
        .root
        .render(ConfigRenderOptions.concise().setJson(true))
      _root_.io.circe.parser.parse(jsonString).right.get
    }
  }
  implicit class ConfigHelper(sc: StringContext) {
    def conf(args: Any*): Config = {
      ConfigFactory.parseString(sc.s(args: _*))
    }
  }

}
