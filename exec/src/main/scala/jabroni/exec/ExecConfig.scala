package jabroni.exec

import java.util.concurrent.TimeUnit

import akka.http.scaladsl.model.Multipart
import akka.http.scaladsl.server.Route
import com.typesafe.config.{Config, ConfigFactory}
import jabroni.api._
import jabroni.api.`match`.MatchDetails
import jabroni.api.exchange.MatchObserver
import jabroni.exec.dao.{ExecDao, UploadDao}
import jabroni.exec.log._
import jabroni.exec.model.RunProcess
import jabroni.exec.rest.ExecutionRoutes
import jabroni.exec.run.ProcessRunner
import jabroni.rest.exchange.ExchangeRoutes
import jabroni.rest.worker.{WorkContext, WorkerConfig, WorkerRoutes}
import jabroni.rest.{RunningService, configForArgs}

import scala.concurrent.duration._


class ExecConfig(execConfig: Config) extends WorkerConfig(execConfig) {


  override def withFallback[C <: WorkerConfig](fallback: C): ExecConfig = new ExecConfig(config.withFallback(fallback.config))

  def add(other: Config): ExecConfig = ExecConfig(other).withFallback(this)

  def ++(map: Map[String, String]): ExecConfig = {
    add(map.asConfig)
  }

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

  def newRunner(ctxt: WorkContext[Multipart.FormData], jobId: JobId): ProcessRunner = {
    import serverImplicits._

    require(system.whenTerminated.isCompleted == false, "Actor system is terminated")

    val uploadDao: UploadDao.FileUploadDao = {
      val uploadDaoOpt = uploads.dir(jobId).map { dir =>
        UploadDao(dir)
      }
      uploadDaoOpt.getOrElse(UploadDao())
    }

    ProcessRunner(
      uploadDao,
      workDir = workingDirectory.dir(jobId),
      newLogger(jobId, ctxt.matchDetails))
  }

  /**
    * @return a handler which will operate on the work requests coming from an exchange ...
    *         or at least from a client who was redirected to us via the exchange
    */
  lazy val handler: ExecutionHandler = ExecutionHandler(this)

  def writeDownRequests = config.getBoolean("writeDownRequests")

  override def landingPage = "ui/run.html"

  def start() = {
    val (executionRoutes, restRoutes) = buildRoutes()

    RunningService.start[ExecConfig, ExecutionRoutes](this, restRoutes, executionRoutes)
  }

  def buildRoutes(): (ExecutionRoutes, Route) = {
    // either attach to or create a new exchange
    val (exchange, optionalExchangeRoutes) = if (includeExchangeRoutes) {
      val obs = MatchObserver()
      val localExchange = exchangeConfig.newExchange(obs)
      val exRoutes: ExchangeRoutes = exchangeConfig.newExchangeRoutes(localExchange)
      (localExchange, Option(exRoutes.routes))
    } else {
      (exchangeClient, None)
    }

    // create something to actually process jobs
    val handler = ExecutionHandler(this)

    // create a worker to subscribe to the exchange
    val workerRoutes: WorkerRoutes = newWorkerRoutes(exchange)
    workerRoutes.addMultipartHandler(handler.onExecute)(subscription, initialRequest)

    // finally create the routes for this REST service, which will include:
    // 1) optionally exchange endpoints if we can act as an exchange
    // 2) worker routes for handling work rerouted from exchanges to us
    // 3) our own custom endpoints which will handle direct job submissions
    val executionRoutes = ExecutionRoutes(this)
    val restRoutes: Route = executionRoutes.routes(workerRoutes, optionalExchangeRoutes)

    (executionRoutes -> restRoutes)
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
    val ec = apply(configForArgs(args, fallbackConfig))
    ec.withFallback(load())
  }

  def apply(config: Config): ExecConfig = new ExecConfig(config)

  def unapply(config: ExecConfig) = Option(config.config)


  def load() = fromRoot(ConfigFactory.load())

  def fromRoot(config: Config) = apply(config.getConfig("exec").ensuring(!_.isEmpty))

}