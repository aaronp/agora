package agora.exec

import agora.exec.model.RunProcess
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.Await

object ExecClientMain extends StrictLogging {
  def main(args: Array[String]): Unit = {
    val conf   = ExecConfig("client.host=localhost" +: "exchange.client.host=localhost" +: args)
    val runner = conf.remoteRunner()
    println(runner)
    val cmd = args.toList match {
      case Nil  => List("whoami")
      case list => list
    }
    val future                   = runner.run(RunProcess(cmd))
    val output: Iterator[String] = Await.result(future, conf.uploadTimeout)
    output.foreach(println)
  }
}
