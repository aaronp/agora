package agora.config

import com.typesafe.config.ConfigRenderOptions._
import com.typesafe.config._

import scala.collection.JavaConverters._
import scala.concurrent.duration._
import scala.language.implicitConversions
import scala.language.postfixOps
import scala.util.Try

/**
  * Provider operations on a 'config'
  */
trait RichConfigOps extends RichConfig.LowPriorityImplicits {

  def config: Config

  import ConfigFactory._
  import RichConfig._

  /** @param key the configuration path
    * @return the value at the given key as a scala duration
    */
  def asDuration(key: String): Duration = {
    config.getString(key).toLowerCase() match {
      case "inf" | "infinite" => Duration.Inf
      case _                  => asFiniteDuration(key)
    }
  }

  def asFiniteDuration(key: String): FiniteDuration = {
    config.getDuration(key).toMillis.millis
  }

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
    def isSimpleList(key: String) = {
      def isList = Try(config.getStringList(key)).isSuccess

      config.hasPath(key) && isList
    }

    def isObjectList(key: String) = {
      def isList = Try(config.getObjectList(key)).isSuccess

      config.hasPath(key) && isList
    }

    val configs: Array[Config] = args.map {
      case KeyValue(k, v) if isSimpleList(k) =>
        asConfig(k, java.util.Arrays.asList(v.split(",", -1): _*))
      case KeyValue(k, v) if isObjectList(k) =>
        sys.error(s"Path '$k' tried to override an object list with '$v'")
      case KeyValue(k, v)    => asConfig(k, v)
      case FilePathConfig(c) => c
      case UrlPathConfig(c)  => c
      case other             => unrecognizedArg(other)
    }

    (configs :+ config).reduce(_ withFallback _)
  }

  /**
    * produces a scala list, either from a StringList or a comma-separated string value
    *
    * @param separator if specified, the value at the given path will be parsed if it is a string and not a stringlist
    * @param path      the config path
    */
  def asList(path: String, separator: Option[String] = Option(",")): List[String] = {
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
  def uniquePaths: List[String] = unique.paths.toList.sorted

  def unique = withoutSystem.filterNot(_.startsWith("akka"))

  /** this config w/o the system props and stuff */
  def withoutSystem: Config = without(systemEnvironment.or(systemProperties).paths)

  def or(other: Config) = config.withFallback(other)

  def without(other: Config): Config = without(asRichConfig(other).paths)

  def without(firstPath: String, theRest: String*): Config = without(firstPath +: theRest)

  def without(paths: TraversableOnce[String]): Config = paths.foldLeft(config)(_ withoutPath _)

  def filterNot(path: String => Boolean) = without(paths.filter(path))

  def describe(implicit opts: ConfigRenderOptions = concise().setFormatted(true)) =
    config.root.render(opts)

  def json = config.root.render(ConfigRenderOptions.concise().setJson(true))

  def paths: List[String] = config.entrySet().asScala.map(_.getKey).toList.sorted

  def pathRoots = paths.map { p =>
    ConfigUtil.splitPath(p).get(0)
  }

  def collectAsStrings: List[(String, String)] = paths.flatMap { key =>
    Try(config.getString(key)).toOption.map(key ->)
  }

  def collectAsMap = collectAsStrings.toMap

  def intersect(other: Config): Config = {
    withPaths(other.paths)
  }

  def withPaths(first: String, theRest: String*): Config = withPaths(first :: theRest.toList)

  def withPaths(paths: List[String]): Config = {
    paths.map(config.withOnlyPath).reduce(_ withFallback _)
  }
}
