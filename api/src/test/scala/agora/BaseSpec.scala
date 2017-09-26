package agora

import _root_.io.circe.parser.parse
import com.typesafe.config.{ConfigFactory, ConfigRenderOptions}

/**
  * Adds json string context to the base spec:
  * {{{
  *   val foo : Json = json""" "foo" : "bar" """
  *
  * }}}
  */
abstract class BaseSpec extends BaseIOSpec {

  implicit class JsonHelper(sc: StringContext) {
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
}
