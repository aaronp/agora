package agora.exec

import agora.exec.model.{FileResult, RunProcess, StreamingResult}
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.Await

object ExecClientMain extends StrictLogging {
  def main(args: Array[String]): Unit = {
    val conf = ExecConfig(args)
    conf.show() match {
      case Some(str) => println(str)
      case None =>
        val runner = conf.remoteRunner()
        val cmd = args.toList match {
          case Nil  => List("whoami")
          case list => list
        }
        val future = runner.run(RunProcess(cmd))
        Await.result(future, conf.uploadTimeout) match {
          case StreamingResult(output) =>
            output.foreach(println)
          case result: FileResult =>
            println(result)
        }
    }

  }
}
