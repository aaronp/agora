package agora.exec.test

import agora.exec.ExecConfig
import agora.exec.client.RemoteRunner

import scala.compat.Platform
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Try

object ExecDebugClient extends App {

  implicit class RF[T](val f: Future[T]) extends AnyVal {
    def block = Await.result(f, 20.seconds)
  }

  val started = Platform.currentTime

  val execConf             = ExecConfig("client.port=80", "exchange.client.port=80")
  val client: RemoteRunner = execConf.remoteRunner()

  val timesPar = (0 to 100).par.map { i =>
    val before = Platform.currentTime
    val pwd = try {
      client.stream("pwd").block.output.mkString("")
    } catch {
      case e =>
        println(e)
    }
    Platform.currentTime - before
  }
  val times = timesPar.seq
  def report(x: Seq[Long]) = {
    val max   = x.max
    val min   = x.min
    val total = x.sum
    val mean  = (total.toDouble / x.size)
    s"""
       | max :$max
       | min :$min
       | mean :$mean
       | total:$total
     """.stripMargin
  }

  println(s"""
       |All: 
       |${report(times)}
       |
       |
       |Removed Outliers: 
       |${report(times.sorted.drop(3).dropRight(3))}
       |
       |
       |
     """.stripMargin)
  execConf.close()
}
