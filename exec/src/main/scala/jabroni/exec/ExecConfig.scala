package jabroni.exec

import java.nio.file.Path
import java.util.concurrent.TimeUnit

import com.typesafe.config.{Config, ConfigFactory}
import jabroni.rest.multipart.MultipartPieces
import jabroni.rest.worker.{WorkContext, WorkerConfig}

import scala.concurrent.duration._
import scala.util.Try

class ExecConfig(val workerConfig: WorkerConfig) {
  def exec = workerConfig.config.getConfig("exec")

  import jabroni.domain.io.implicits._
  import workerConfig.implicits._

  private lazy val executionRoutes = {
    ExecutionRoutes(this)
  }

  /** @return a process runner to execute the given request
    */
  def newRunner(req: WorkContext[MultipartPieces]): ProcessRunner = {
    import jabroni.api.nextJobId
    val jobId = req.matchDetails.map(_.jobId).getOrElse(nextJobId)
    val logDir = baseLogDir.map(_.resolve(jobId).mkDirs())
    val uploadDir = baseUploadDir.resolve(jobId).mkDirs()
    val workDir = baseWorkDir.map(_.resolve(jobId).mkDirs())
    ProcessRunner(uploadDir, workDir, logDir, errorLimit, includeConsoleAppender)
  }

  def start() = {
    executionRoutes.start
  }

  override def toString = exec.root.render()

  def baseWorkDir = Try(exec.getString("workDir")).toOption.filterNot(_.isEmpty).map(_.asPath.mkDirs())

  def baseUploadDir = exec.getString("uploadDir").asPath.mkDirs()

  def includeConsoleAppender = exec.getBoolean("includeConsoleAppender")

  def baseLogDir: Option[Path] = Try(exec.getString("logDir")).toOption.filterNot(_.isEmpty).map(_.asPath.mkDirs())

  def allowTruncation = exec.getBoolean("allowTruncation")

  def maximumFrameLength = exec.getInt("maximumFrameLength")

  def errorLimit = Option(exec.getInt("errorLimit")).filter(_ > 0)

  implicit def uploadTimeout: FiniteDuration = exec.getDuration("uploadTimeout", TimeUnit.MILLISECONDS).millis

  def remoteRunner(): ProcessRunner with AutoCloseable = {

    import workerConfig.implicits._

    ProcessRunner(workerConfig.exchangeClient, maximumFrameLength, allowTruncation)
  }
}

object ExecConfig {

  def apply(args: Array[String] = Array.empty, fallbackConfig: Config = defaultConfig) = {
    new ExecConfig(WorkerConfig(args, fallbackConfig))
  }

  def defaultConfig = {
    val execConf = ConfigFactory.parseResourcesAnySyntax("exec")
    execConf.withFallback(WorkerConfig.baseConfig())
  }

}