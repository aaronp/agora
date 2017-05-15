package jabroni.exec

import java.util.concurrent.TimeUnit

import com.typesafe.config.ConfigFactory
import jabroni.exec.ExecutionWorker.onRun
import jabroni.rest.exchange.ExchangeClient
import jabroni.rest.multipart.MultipartPieces
import jabroni.rest.worker.{WorkContext, WorkerConfig}

import scala.concurrent.duration._

class ExecConfig(val workerConfig: WorkerConfig) {
  def exec = workerConfig.config.getConfig("exec")

  import workerConfig.implicits._
  import jabroni.domain.io.implicits._

  private lazy val configuredWorker = {
    workerConfig.workerRoutes.addMultipartHandler { (req: WorkContext[MultipartPieces]) =>
      import jabroni.api._
      val jobDir = workDir.resolve(req.matchDetails.map(_.jobId).getOrElse("_" + nextJobId())).mkDirs()
      val runner = ProcessRunner(jobDir, errorLimit)
      req.completeWith(onRun(runner, req, uploadTimeout))
    }
    workerConfig
  }

  def start() = configuredWorker.startWorker

  override def toString = exec.root.render()

  def workDir = exec.getString("workDir").asPath.mkDirs()

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

  def apply(args: Array[String] = Array.empty) = {
    new ExecConfig(WorkerConfig(args, defaultConfig))
  }

  def defaultConfig = {
    val execConf = ConfigFactory.parseResourcesAnySyntax("exec")
    execConf.withFallback(WorkerConfig.baseConfig())
  }

}