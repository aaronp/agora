package agora.exec

import java.util.concurrent.TimeUnit

import akka.http.scaladsl.model.Multipart
import akka.http.scaladsl.server.Route
import com.typesafe.config.{Config, ConfigFactory}
import agora.api._
import agora.api.`match`.MatchDetails
import agora.api.exchange.MatchObserver
import agora.exec.dao.{ExecDao, UploadDao}
import agora.exec.log._
import agora.exec.model.RunProcess
import agora.exec.rest.ExecutionRoutes
import agora.exec.run.{LocalRunner, ProcessRunner}
import agora.rest.exchange.ExchangeRoutes
import agora.rest.worker.{WorkContext, WorkerConfig, WorkerRoutes}
import agora.rest.{RunningService, ServerConfig, configForArgs}
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller

import scala.concurrent.duration._

class ExecConfig(execConfig: Config) extends WorkerConfig(execConfig) {

  override def withFallback(fallback: Config): ExecConfig = new ExecConfig(config.withFallback(fallback))

  override def withOverrides(overrides: Config): ExecConfig = new ExecConfig(overrides).withFallback(config)

  /** @return a process runner to execute the given request
    */
  def newLogger(jobId: JobId, matchDetails: Option[MatchDetails]): (RunProcess) => IterableLogger = {

    def mkLogger(proc: RunProcess): IterableLogger = {
      val iterLogger = IterableLogger(proc, matchDetails, errorLimit)
      if (includeConsoleAppender) {
        iterLogger.add(loggingProcessLogger(logPrefix(proc)))
      }

      logs.dir(jobId).foreach { dir =>
        iterLogger.addUnderDir(dir)
      }

      iterLogger
    }

    mkLogger _
  }

  def newRunner(matchDetails: Option[MatchDetails], jobId: JobId): LocalRunner = {
    import serverImplicits._

    require(system.whenTerminated.isCompleted == false, "Actor system is terminated")

    val uploadDao: UploadDao.FileUploadDao = {
      val uploadDaoOpt = uploads.dir(jobId).map { dir =>
        UploadDao(dir)
      }
      uploadDaoOpt.getOrElse(UploadDao())
    }

    val runner: LocalRunner = ProcessRunner(uploadDao, workDir = workingDirectory.dir(jobId), newLogger(jobId, matchDetails))
    runner
  }

  /**
    * @return a handler which will operate on the work requests coming from an exchange ...
    *         or at least from a client who was redirected to us via the exchange
    */
  lazy val handler: ExecutionHandler = ExecutionHandler(this)

  def writeDownRequests = config.getBoolean("writeDownRequests")

  override def landingPage = "ui/run.html"

  def start() = {
    val execSys    = ExecSystem(this)
    val restRoutes = execSys.routes
    RunningService.start[ExecConfig, ExecutionRoutes](this, restRoutes, execSys.executionRoutes)
  }

  lazy val logs = PathConfig(execConfig.getConfig("logs").ensuring(!_.isEmpty))

  lazy val uploads = PathConfig(execConfig.getConfig("uploads").ensuring(!_.isEmpty))

  def uploadsDir = uploads.path.getOrElse(sys.error("Invalid configuration - no uploads directory set"))

  def execDao = ExecDao(uploadsDir)(serverImplicits.executionContext)

  lazy val workingDirectory = PathConfig(execConfig.getConfig("workingDirectory").ensuring(!_.isEmpty))

  override def toString = execConfig.root.render()

  def includeConsoleAppender = execConfig.getBoolean("includeConsoleAppender")

  def appendJobIdToLogDir: Boolean = execConfig.getBoolean("appendJobIdToLogDir")

  def appendJobIdToWorkDir: Boolean = execConfig.getBoolean("appendJobIdToLogDir")

  def appendJobIdToUploadDir: Boolean = execConfig.getBoolean("appendJobIdToUploadDir")

  def allowTruncation = execConfig.getBoolean("allowTruncation")

  def replaceWorkOnFailure = execConfig.getBoolean("replaceWorkOnFailure")

  def defaultFrameLength = execConfig.getInt("defaultFrameLength")

  def errorLimit = Option(execConfig.getInt("errorLimit")).filter(_ > 0)

  implicit def uploadTimeout: FiniteDuration = execConfig.getDuration("uploadTimeout", TimeUnit.MILLISECONDS).millis

  def remoteRunner(): ProcessRunner with AutoCloseable = {
    ProcessRunner(exchangeClient, defaultFrameLength, allowTruncation, replaceWorkOnFailure)
  }
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
