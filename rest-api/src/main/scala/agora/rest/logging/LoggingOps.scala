package agora.rest.logging

import java.io.InputStream
import java.lang.reflect.Field

import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.classic.{Level, Logger, LoggerContext}
import org.slf4j.LoggerFactory
import org.slf4j.helpers.SubstituteLoggerFactory

import scala.util.Try

object LoggingOps {

  def context(): Option[LoggerContext] = {
    LoggerFactory.getILoggerFactory match {
      case ctxt: LoggerContext => Option(ctxt)
      case slf: SubstituteLoggerFactory =>
        import scala.collection.JavaConverters._
        val all = slf.getLoggers.asScala.toList

        all.foreach(println)

        None
      case _ => None
    }
  }

  def setInfo(name: String) = setLevel(name, "info")

  def setDebug(name: String) = setLevel(name, "debug")

  def setWarn(name: String) = setLevel(name, "warn")

  def setError(name: String) = setLevel(name, "error")

  /** @param name  the logger name
    * @param level the new log level (e.g. debug, info, warn, error)
    * @return the previous and current log levels
    */
  def setLevel(name: String, level: String): Option[(String, String)] = {
    context().map { ctxt =>
      val newLevel = Level.valueOf(level)
      val logger   = ctxt.getLogger(name)
      val oldLevel = logger.getLevel
      LoggerFactory.getLogger("agora").error(s"Changing the '$name' log level from $oldLevel to $newLevel")
      logger.setLevel(newLevel)
      (Option(oldLevel).map(_.levelStr).getOrElse("not set"), Option(newLevel).map(_.levelStr).getOrElse("not set"))
    }
  }

  /**
    * Execute some code w/ an appender at a given log level.
    * Upon exit the log level will revert back to its previous level, probably.
    *
    * @param name the logger name
    * @param level an optional log level to ensure is set (e.g. debug, info, warn...)
    * @param thunk the think to execute within the scope of this log change
    * @tparam T
    * @return the result of the computation
    */
  def withLogs[T](name: String, level: String = null)(thunk: Option[BufferedAppender] => T): T = {
    context().map(_.getLogger(name)) match {
      case Some(logger) =>
        val putBackToLevel = if (level != null) {
          val oldLevel: Level = logger.getLevel
          val newLevel: Level = Level.valueOf(level)
          if (oldLevel != newLevel) {
            logger.setLevel(newLevel)
            Option(oldLevel)
          } else {
            None
          }
        } else {
          None
        }

        val appender = BufferedAppender()
        appender.start()

        logger.addAppender(appender)

        try {
          val result: T = thunk(Option(appender))
          result
        } finally {
          logger.detachAppender(appender.getName)
          putBackToLevel.foreach(logger.setLevel)
        }

      case None => thunk(None)
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
