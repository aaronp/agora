package agora.exec

import java.util.concurrent.TimeUnit

import agora.api._
import agora.api.`match`.MatchDetails
import agora.exec.log._
import agora.exec.model.RunProcess
import agora.exec.rest.ExecutionRoutes
import agora.exec.run.{LocalRunner, ProcessRunner}
import agora.rest.worker.WorkerConfig
import agora.rest.{RunningService, configForArgs}
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.duration._

class ExecConfig(execConfig: Config) extends WorkerConfig(execConfig) {

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
  def newRunner(proc: RunProcess, matchDetails: Option[MatchDetails], jobId: JobId): LocalRunner = {
    import serverImplicits._
    require(system.whenTerminated.isCompleted == false, "Actor system is terminated")
    ProcessRunner(workDir = workingDirectory.dir(jobId), newLogger(_, jobId, matchDetails))
  }

  /**
    * @return a handler which will operate on the work requests coming from an exchange ...
    *         or at least from a client who was redirected to us via the exchange
    */
  lazy val handler: ExecutionHandler = ExecutionHandler(this)

  override def landingPage = "ui/run.html"

  def start() = {
    val execSys    = ExecSystem(this)
    val restRoutes = execSys.routes
    RunningService.start[ExecConfig, ExecutionRoutes](this, restRoutes, execSys.executionRoutes)
  }

  lazy val logs = PathConfig(execConfig.getConfig("logs").ensuring(!_.isEmpty))

  lazy val uploads = PathConfig(execConfig.getConfig("uploads").ensuring(!_.isEmpty))

  def uploadsDir = uploads.path.getOrElse(sys.error("Invalid configuration - no uploads directory set"))

  lazy val workingDirectory = PathConfig(execConfig.getConfig("workingDirectory").ensuring(!_.isEmpty))

  override def toString = execConfig.root.render()

  def includeConsoleAppender = execConfig.getBoolean("includeConsoleAppender")
//
//  def appendJobIdToLogDir: Boolean = execConfig.getBoolean("appendJobIdToLogDir")
//
//  def appendJobIdToWorkDir: Boolean = execConfig.getBoolean("appendJobIdToLogDir")
//
//  def appendJobIdToUploadDir: Boolean = execConfig.getBoolean("appendJobIdToUploadDir")

  def allowTruncation = execConfig.getBoolean("allowTruncation")

  def replaceWorkOnFailure = execConfig.getBoolean("replaceWorkOnFailure")

  def defaultFrameLength = execConfig.getInt("defaultFrameLength")

  def errorLimit = Option(execConfig.getInt("errorLimit")).filter(_ > 0)

  implicit def uploadTimeout: FiniteDuration = execConfig.getDuration("uploadTimeout", TimeUnit.MILLISECONDS).millis

  def remoteRunner() = {
    ProcessRunner(exchangeClient, defaultFrameLength, allowTruncation, replaceWorkOnFailure)
  }

  //  def sessionRunner() = SessionRunner(exchangeClient, defaultFrameLength, allowTruncation)
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
