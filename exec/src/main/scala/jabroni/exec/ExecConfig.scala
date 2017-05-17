package jabroni.exec

import java.nio.file.Path
import java.util.concurrent.TimeUnit

import com.typesafe.config.{Config, ConfigFactory}
import jabroni.api.JobId
import jabroni.rest.{BaseConfig, configForArgs}
import jabroni.rest.multipart.MultipartPieces
import jabroni.rest.worker.{WorkContext, WorkerConfig}

import scala.concurrent.duration._
import scala.util.Try
import jabroni.domain.io.implicits._

case class PathConfig(config: Config) {
  val appendJobId: Boolean = config.getBoolean("appendJobId")

  def mkDirs: Boolean = config.getBoolean("mkDirs")

  private def pathOpt = Try(config.getString("dir")).toOption.filterNot(_.isEmpty).map(_.asPath)

  val path = pathOpt.map {
    case p if mkDirs => p.mkDirs()
    case p => p
  }

  def dir(jobId: JobId): Option[Path] = path.map {
    case p if appendJobId => p.resolve(jobId).mkDir()
    case p => p
  }
}

trait ExecConfig extends WorkerConfig {

  //override type Me = ExecConfig

  def exec = config.getConfig("exec")

  import implicits._

  private lazy val executionRoutes = {
    ExecutionRoutes(this)
  }

  /** @return a process runner to execute the given request
    */
  def newRunner(req: WorkContext[MultipartPieces]): ProcessRunner = {
    import jabroni.api.nextJobId
    val jobId: JobId = req.matchDetails.map(_.jobId).getOrElse(nextJobId)
    ProcessRunner(
      uploadDir = uploads.dir(jobId).getOrElse(sys.error("uploadDir not set")),
      description = jobId,
      workDir = workingDirectory.dir(jobId),
      logDir = logs.dir(jobId),
      errorLimit = errorLimit,
      includeConsoleAppender = includeConsoleAppender)
  }

  def start() = {
    executionRoutes.start
  }

  val logs = PathConfig(exec.getConfig("logs").ensuring(!_.isEmpty))

  val uploads = PathConfig(exec.getConfig("uploads").ensuring(!_.isEmpty)).ensuring(_.path.isDefined, "uploads directory must be set")

  val workingDirectory = PathConfig(exec.getConfig("workingDirectory").ensuring(!_.isEmpty))

  override def toString = exec.root.render()

  def includeConsoleAppender = exec.getBoolean("includeConsoleAppender")

  def appendJobIdToLogDir: Boolean = exec.getBoolean("appendJobIdToLogDir")

  def appendJobIdToWorkDir: Boolean = exec.getBoolean("appendJobIdToLogDir")

  def appendJobIdToUploadDir: Boolean = exec.getBoolean("appendJobIdToUploadDir")

  def allowTruncation = exec.getBoolean("allowTruncation")

  def maximumFrameLength = exec.getInt("maximumFrameLength")

  def errorLimit = Option(exec.getInt("errorLimit")).filter(_ > 0)

  implicit def uploadTimeout: FiniteDuration = exec.getDuration("uploadTimeout", TimeUnit.MILLISECONDS).millis

  def remoteRunner(): ProcessRunner with AutoCloseable = {
    ProcessRunner(exchangeClient, maximumFrameLength, allowTruncation)
  }
}

object ExecConfig {

  def apply(args: Array[String] = Array.empty, fallbackConfig: Config = defaultConfig): ExecConfig = {
    apply(configForArgs(args, defaultConfig))
  }

  def apply(config: Config): ExecConfig = new BaseConfig(config.resolve) with ExecConfig

  def defaultConfig = {
    val execConf = ConfigFactory.parseResourcesAnySyntax("exec")
    execConf.withFallback(WorkerConfig.baseConfig())
  }

}