package agora.exec.test

import agora.exec.ExecConfig

import scala.concurrent.Await
import scala.concurrent.duration.Duration
import scala.io.StdIn

object ExecDebugRun extends App {
  val fut = ExecConfig(args).start()
  val res = Await.result(fut, Duration.Inf)
  println(res)
  StdIn.readLine()
}
