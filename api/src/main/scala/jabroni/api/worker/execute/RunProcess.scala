package jabroni.api.worker.execute

import java.nio.file.Path

import io.circe.JsonObject
import io.circe.optics.JsonPath
import jabroni.api.json.{JMatcher, JPart}
import jabroni.api.worker.DispatchWork

import scala.collection.mutable.ListBuffer
import scala.concurrent.{ExecutionContext, Future}
import scala.sys.process.ProcessLogger

class RunProcess(workingDir: Option[Path])(implicit val ec: ExecutionContext) {

  import RunProcess._

  def run(cmd: String,
          args: Seq[String] = Nil,
          env: Map[String, String] = Map.empty,
          maxLogLen: Option[Int] = None): Future[Iterator[String]] = {
    import sys.process._
    val p: ProcessBuilder = Process(cmd +: args, workingDir.map(_.toFile), env.toSeq: _*)
    val log = new Logger(maxLogLen)

    import RunProcess._

    Future {
      p.lineStream(log).iterator
    }
  }

  def apply(work: DispatchWork): Option[Future[Iterator[String]]] = {
    val json = work.job.job
    for {
      c <- cmd.getOption(json)
      a <- args.getOption(json)
      e <- env.getOption(json)
      maxLen = maxLogLen.getOption(json)
    } yield {
      run(c, a.flatMap(_.asString), asKeyValue(e), maxLen)
    }
  }

}

object RunProcess {

  def apply(workingDir: Option[Path] = None)(implicit ec: ExecutionContext = ExecutionContext.Implicits.global): RunProcess = {
    new RunProcess(workingDir)
  }

  def asKeyValue(obj: JsonObject) = {
    val map: Map[String, Option[String]] = obj.toMap.mapValues(_.asString)
    map.collect {
      case (key, Some(value)) => (key, value)
    }
  }


  class Logger(maxErrLen: Option[Int]) extends ProcessLogger {
    var output = ListBuffer[String]()
    var error = ListBuffer[String]()

    override def out(s: => String): Unit = {
      output += s
    }

    override def err(s: => String): Unit = {
      val stopLoggingToErr = maxErrLen.exists(error.size > _)
      if (!stopLoggingToErr) {
        error += s
      }
    }

    override def buffer[T](f: => T): T = f
  }

  val cmd = JsonPath.root.cmd.string
  val args = JsonPath.root.args.arr
  val env = JsonPath.root.env.obj
  val maxLogLen = JsonPath.root.maxLogLen.int

  def matcher: JMatcher = {
    JPart("cmd").and(JPart("args")).and(JPart("env"))
  }
}
