package agora.config

import java.nio.file.{Files, Paths}

import com.typesafe.config.{Config, ConfigFactory, ConfigUtil}

/**
  * Adds some scala utility around a typesafe config
  *
  * @param config
  */
class RichConfig(override val config: Config) extends RichConfigOps

object RichConfig {

  trait LowPriorityImplicits {

    implicit class RichString(val str: String) {
      def quoted = ConfigUtil.quoteString(str)
    }

    implicit def asRichConfig(c: Config): RichConfig = new RichConfig(c)

    implicit class RichArgs(val args: Array[String]) {
      def asConfig(unrecognizedArg: String => Config = ParseArg.Throw): Config = {
        ConfigFactory.empty().withUserArgs(args, unrecognizedArg)
      }
    }

    implicit class RichMap(val map: Map[String, String]) {
      def asConfig: Config = {
        import scala.collection.JavaConverters._
        ConfigFactory.parseMap(map.asJava)
      }
    }

  }

  /**
    * Contains functions detailing what to do with user command-line input
    * which doesn't match either a file path, resource or key=value pair
    */
  object ParseArg {
    val Throw         = (a: String) => sys.error(s"Unrecognized user arg '$a'")
    val Ignore        = (a: String) => ConfigFactory.empty()
    val AsBooleanFlag = (a: String) => asConfig(ConfigUtil.quoteString(a), true.toString)
  }

  def asConfig(key: String, value: Any): Config = {
    import scala.collection.JavaConverters._
    ConfigFactory.parseMap(Map(key -> value).asJava)
  }

  private[config] object FilePathConfig {
    def unapply(path: String): Option[Config] = {
      Option(Paths.get(path))
        .filter(p => Files.exists(p))
        .map(_.toFile)
        .map(ConfigFactory.parseFileAnySyntax)
    }
  }

  private[config] object UrlPathConfig {
    def unapply(path: String): Option[Config] = {
      val url = getClass.getClassLoader.getResource(path)
      Option(url).map(ConfigFactory.parseURL)
    }
  }

  private[config] val KeyValue = "(.*)=(.*)".r

}
