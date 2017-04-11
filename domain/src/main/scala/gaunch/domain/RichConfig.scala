package jabroni.domain

import java.nio.file.{Files, Paths}

import com.typesafe.config._

import scala.language.implicitConversions

/**
  * Adds some scala utility around a typesafe config
  *
  * @param config
  */
class RichConfig(val config: Config) {

  import RichConfig._

  /**
    * If 'show' specified, either by just 'show' on its own or 'show=path.to.config.or.value', then this will return
    * the configuration at that path
    *
    * @return the optional value of what's pointed to if 'show=<path>' is specified
    */
  def show(options: ConfigRenderOptions = ConfigRenderOptions.concise().setFormatted(true)): Option[String] = {
    if (config.hasPath("show")) {
      Option(config.withOnlyPath(config.getString("show"))).map(_.root.render(options))
    } else {
      None
    }
  }

  /**
    * @param args            the user arguments in the form <key>=<value>, <filePath> or <fileOnTheClasspath>
    * @param unrecognizedArg what to do with malformed user input
    * @return a configuration with the given user-argument overrides applied over top
    */
  def withUserArgs(args: Array[String], unrecognizedArg: String => Config = ParseArg.Throw): Config = {
    val configs: Array[Config] = args.map {
      case KeyValue(k, v) => asConfig(k, v)
      case FilePathConfig(c) => c
      case UrlPathConfig(c) => c
      case other => unrecognizedArg(other)
    }

    (configs :+ config).reduce(_ withFallback _)
  }

  /**
    * produces a scala list, either from a StringList or a comma-separated string value
    *
    * @param separator if specified, the value at the given path will be parsed if it is a string and not a stringlist
    * @param path      the config path
    */
  def asList(path: String, separator: Option[String] = Option(",")) = {
    import collection.JavaConverters._
    try {
      config.getStringList(path).asScala.toList
    } catch {
      case e: ConfigException.WrongType =>
        separator.fold(throw e) { sep =>
          config.getString(path).split(sep, -1).toList
        }
    }
  }

}

object RichConfig {

  /**
    * Exposes the entry point for using a RichConfig,
    *
    * mostly for converting user-args into a config
    */
  object implicits {

    implicit class RichString(val str: String) extends AnyVal {
      def quoted = ConfigUtil.quoteString(str)
    }

    implicit def asRichConfig(c: Config) = new RichConfig(c)

    implicit class RichArgs(val args: Array[String]) extends AnyVal {
      def asConfig(unrecognizedArg: String => Config = ParseArg.Throw): Config = {
        ConfigFactory.load().withUserArgs(args, unrecognizedArg)
      }
    }

  }

  /**
    * Contains functions detailing what to do with user command-line input
    * which doesn't match either a file path, resource or key=value pair
    */
  object ParseArg {
    val Throw = (a: String) => sys.error(s"Unrecognized user arg '$a'")
    val Ignore = (a: String) => ConfigFactory.empty()
    val AsBooleanFlag = (a: String) => asConfig(ConfigUtil.quoteString(a), true.toString)
  }

  def asConfig(key: String, value: String) = {
    import collection.JavaConverters._
    ConfigFactory.parseMap(Map(key -> value).asJava)
  }

  private object FilePathConfig {
    def unapply(path: String): Option[Config] = {
      Option(Paths.get(path)).filter(p => Files.exists(p)).
        map(_.toFile).
        map(ConfigFactory.parseFileAnySyntax)
    }
  }

  private object UrlPathConfig {
    def unapply(path: String): Option[Config] = {
      val url = getClass.getClassLoader.getResource(path)
      Option(url).map(ConfigFactory.parseURL)
    }
  }

  private val KeyValue = "(.*)=(.*)".r

}
