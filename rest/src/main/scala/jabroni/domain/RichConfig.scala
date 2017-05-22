package jabroni.domain

import java.nio.file.{Files, Paths}

import com.typesafe.config._

import scala.language.implicitConversions

trait RichConfigOps  extends RichConfig.LowPriorityImplicits {

  def config : Config

  import ConfigFactory._
  import RichConfig._

  import scala.collection.JavaConverters._

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


  /** And example which uses most of the below stuff to showcase what this is for
    * Note : writing a 'diff' using this would be pretty straight forward
    */
  def uniquePaths = unique.paths.toList.sorted
  def unique = withoutSystem.filterNot(_.startsWith("akka"))
  /** this config w/o the system props and stuff */
  def withoutSystem: Config = without(systemEnvironment.or(systemProperties).paths)
  def or(other: Config) = config.withFallback(other)
  def without(other: Config): Config = without(asRichConfig(other).paths)
  def without(firstPath: String, theRest: String*): Config = without(firstPath +: theRest)
  def without(paths: TraversableOnce[String]): Config = paths.foldLeft(config)(_ withoutPath _)
  def filterNot(path : String => Boolean) = without(paths.filter(path))
  def describe(implicit opts: ConfigRenderOptions = ConfigRenderOptions.concise().setFormatted(true)) = config.root.render(opts)
  def paths: Set[String] = config.entrySet().asScala.map(_.getKey).toSet
  def pathRoots = paths.map { p => ConfigUtil.splitPath(p).get(0) }

  def intersect(other: Config): Config = {
    withPaths(other.paths)
  }
  def withPaths(first : String, theRest : String*): Config = withPaths(theRest.toSet + first)
  def withPaths(paths : Set[String]): Config = {
    paths.map(config.withOnlyPath).reduce(_ withFallback _)
  }
}

/**
  * Adds some scala utility around a typesafe config
  *
  * @param config
  */
class RichConfig(override val config: Config) extends RichConfigOps

object RichConfig {

  /**
    * Exposes the entry point for using a RichConfig,
    *
    * mostly for converting user-args into a config
    */
  object implicits extends LowPriorityImplicits

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
      def asConfig : Config = {
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
    val Throw = (a: String) => sys.error(s"Unrecognized user arg '$a'")
    val Ignore = (a: String) => ConfigFactory.empty()
    val AsBooleanFlag = (a: String) => asConfig(ConfigUtil.quoteString(a), true.toString)
  }

  def asConfig(key: String, value: String) = {
    import collection.JavaConverters._
    ConfigFactory.parseMap(Map(key -> value).asJava)
  }

  private[domain]  object FilePathConfig {
    def unapply(path: String): Option[Config] = {
      Option(Paths.get(path)).filter(p => Files.exists(p)).
        map(_.toFile).
        map(ConfigFactory.parseFileAnySyntax)
    }
  }

  private[domain]  object UrlPathConfig {
    def unapply(path: String): Option[Config] = {
      val url = getClass.getClassLoader.getResource(path)
      Option(url).map(ConfigFactory.parseURL)
    }
  }

  private[domain] val KeyValue = "(.*)=(.*)".r

}
