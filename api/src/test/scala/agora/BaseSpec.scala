package agora

import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

import _root_.io.circe.parser.parse
import com.typesafe.config.{Config, ConfigFactory, ConfigRenderOptions}

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
  implicit class ConfigHelper(sc: StringContext) {
    def conf(args: Any*): Config = {
      ConfigFactory.parseString(sc.s(args: _*))
    }
  }

  val daemonicExecutor = java.util.concurrent.Executors.newFixedThreadPool(
    4,
    new ThreadFactory {
      val id = new AtomicInteger(0)
      override def newThread(r: Runnable): Thread = {
        val thread = new Thread(r)
        thread.setDaemon(true)
        thread.setName(s"${id.incrementAndGet()}-thread")
        thread
      }
    }
  )
}
