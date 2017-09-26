package example.automatedturk

import agora.rest.worker.WorkerConfig

import scala.io.StdIn

object TurkClient extends App {

  def readNext() = StdIn.readLine("question:")

  var line = readNext()

  val conf = WorkerConfig(args)
  import conf.clientConfig

  while (!line.startsWith("quit")) {

    line = readNext()
  }
}
