package agora.exec.test

import agora.exec.ExecConfig

import concurrent.duration._
import scala.concurrent.{Await, Future}

object ExecDebugClient extends App {
  implicit class RF[T](val f: Future[T]) extends AnyVal {
    def block = Await.result(f, 20.seconds)
  }
  val client = ExecConfig().remoteRunner()
  val pwd    = client.stream("pwd").block.mkString("")
  println(pwd)
}
