package agora.exec

import java.nio.file.Path
import java.util.concurrent.TimeUnit

import agora.api._
import agora.api.`match`.MatchDetails
import agora.exec.log._
import agora.exec.model.RunProcess
import agora.exec.rest.ExecutionRoutes
import agora.exec.run.{LocalRunner, ProcessRunner}
import agora.exec.workspace.WorkspaceId
import agora.rest.worker.WorkerConfig
import agora.rest.{RunningService, configForArgs}
import akka.http.scaladsl.server.Route
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * Represents a worker configuration which can execute requests
  *
  * @param execConfig
  */
class ExecConfig(execConfig: Config) extends WorkerConfig(execConfig) {

  /** Convenience method for starting an exec service.
    * Though this may seem oddly placed on a configuration, a service and its configuration
    * are tightly coupled, so whether you do :
    * {{{
    *   Service(config)
    * }}}
    * or
    * {{{
    *   config.newService
    * }}}
    * either should be arbitrary. And by doing it this way, hopefully the readability will be better from
    * the point of
    *
    * {{{
    * userArgs => config => started/running service
    * }}}
    *
    * @return a Future of a [[RunningService]]
    */
  def start(): Future[RunningService[ExecConfig, ExecutionRoutes]] = ExecBoot(this).start

  override def withFallback(fallback: Config): ExecConfig = new ExecConfig(config.withFallback(fallback))

  override def withOverrides(overrides: Config): ExecConfig = new ExecConfig(overrides).withFallback(config)

  /** @return a process runner to execute the given request
    */
  def newLogger(proc: RunProcess, jobId: JobId, matchDetails: Option[MatchDetails]): IterableLogger = {

    val iterLogger = IterableLogger(proc, matchDetails, errorLimit)
    if (includeConsoleAppender) {
      iterLogger.add(loggingProcessLogger(logPrefix(proc)))
    }

    logs.dir(jobId).foreach { dir =>
      iterLogger.addUnderDir(dir)
    }

    iterLogger
  }

  /**
    * Creates a new LocalRunner based on the given match details and job id
    *
    * @param matchDetails
    * @param jobId
    * @return
    */
  def newRunner(proc: RunProcess, matchDetails: Option[MatchDetails], workingDirectory: Option[Path], jobId: JobId): LocalRunner = {
    import serverImplicits._
    require(system.whenTerminated.isCompleted == false, "Actor system is terminated")
    new LocalRunner(workDir = workingDirectory) {
      override def mkLogger(proc: RunProcess) = {
        newLogger(proc, jobId, matchDetails)
      }
    }
  }

  override def landingPage = "ui/run.html"

  lazy val logs = PathConfig(execConfig.getConfig("logs").ensuring(!_.isEmpty))

  lazy val uploads = PathConfig(execConfig.getConfig("uploads").ensuring(!_.isEmpty))

  def uploadsDir = uploads.path.getOrElse(sys.error("Invalid configuration - no uploads directory set"))

  def includeConsoleAppender = execConfig.getBoolean("includeConsoleAppender")

  def allowTruncation = execConfig.getBoolean("allowTruncation")

  def replaceWorkOnFailure = execConfig.getBoolean("replaceWorkOnFailure")

  def defaultFrameLength = execConfig.getInt("defaultFrameLength")

  def errorLimit = Option(execConfig.getInt("errorLimit")).filter(_ > 0)

  def initialExecutionSubscription = execConfig.getInt("initialExecutionSubscription")

  implicit def uploadTimeout: FiniteDuration = execConfig.getDuration("uploadTimeout", TimeUnit.MILLISECONDS).millis

  def remoteRunner() = {
    ProcessRunner(exchangeClient, defaultFrameLength, allowTruncation, replaceWorkOnFailure)
  }

  override def toString = execConfig.root.render()
}

object ExecConfig {

  def apply(firstArg: String, theRest: String*): ExecConfig = apply(firstArg +: theRest.toArray)

  def apply(args: Array[String] = Array.empty, fallbackConfig: Config = ConfigFactory.empty): ExecConfig = {
    val ec: ExecConfig = apply(configForArgs(args, fallbackConfig))
    ec.withFallback(load().config)
  }

  def apply(config: Config): ExecConfig = new ExecConfig(config)

  def unapply(config: ExecConfig) = Option(config.config)

  def load() = fromRoot(ConfigFactory.load())

  def fromRoot(config: Config): ExecConfig = apply(config.getConfig("exec").ensuring(!_.isEmpty))

}
