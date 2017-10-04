package agora.rest.support

import java.io.{FileInputStream, InputStream}

import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.classic.{Level, LoggerContext}
import org.slf4j.LoggerFactory

import scala.sys.SystemProperties
import scala.util.Try

object LoggingOps {

  def context(): Option[LoggerContext] = {
    LoggerFactory.getILoggerFactory match {
      case ctxt: LoggerContext => Option(ctxt)
      case _                   => None
    }
  }

  def setInfo(name: String) = setLevel(name, "info")

  def setDebug(name: String) = setLevel(name, "debug")

  def setWarn(name: String) = setLevel(name, "warn")

  def setError(name: String) = setLevel(name, "error")

  def setLevel(name: String, level: String) = {
    context().map { ctxt =>
      val newLevel = Level.valueOf(level)
      val oldLevel = ctxt.getLogger(name).getLevel
      LoggerFactory.getLogger("agora").error(s"Changing the '$name' log level from $oldLevel to $newLevel")
      ctxt.getLogger(name).setLevel(newLevel)
      (Option(oldLevel).map(_.levelStr).getOrElse("not set"), Option(newLevel).map(_.levelStr).getOrElse("not set"))
    }
  }

  private var currentConfig: Option[String] = None

  /** @return the current configuration
    */
  def config(): Option[String] = {

    currentConfig.orElse(readConfig)
  }

  /**
    * @return the config from the congured logback file
    */
  def readConfig(): Option[String] = {
    agora.config.propOrEnv("logback.configurationFile").flatMap { confFile =>
      def fileInstreamOpt: Option[InputStream] = {
        import agora.io.implicits._
        Try(confFile.asPath.inputStream()).toOption
      }
      val classPathOpt: Option[InputStream] = Option(getClass.getClassLoader.getResourceAsStream(confFile))

      classPathOpt.orElse(fileInstreamOpt).map { confStream =>
        val src = scala.io.Source.fromInputStream(confStream)
        try {
          src.getLines().mkString("\n")
        } finally {
          Try(src.close())
        }
      }
    }

  }

  def reset(newConf: String): Boolean = synchronized {
    context().fold(false) { ctxt =>
      currentConfig = Option(newConf)
      val joran = new JoranConfigurator
      joran.setContext(ctxt)

      val stream: java.io.InputStream = new java.io.ByteArrayInputStream(newConf.getBytes)
      joran.doConfigure(stream)

      true
    }
  }
}
