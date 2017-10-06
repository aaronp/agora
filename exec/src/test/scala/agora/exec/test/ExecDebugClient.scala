package agora.exec.test

import agora.exec.ExecConfig
import agora.exec.client.RemoteRunner
import agora.exec.model.RunProcess

import scala.compat.Platform
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.control.NonFatal

object ExecDebugClient extends App {

  implicit class RF[T](val f: Future[T]) extends AnyVal {
    def block = Await.result(f, 20.seconds)
  }

  val cache    = args.headOption.exists(_ == "true")
  val useCache = args.drop(1).headOption.exists(_ == "true")

  val started = Platform.currentTime

  val execConf             = ExecConfig("client.port=80", "exchange.client.port=80")
  val client: RemoteRunner = execConf.remoteRunner()

  val timesPar = (0 to 100).par.map { i =>
    val before = Platform.currentTime
    val output = try {
      val job = RunProcess("$GA_HOME/ga.sh", "102", "11", "14", "7", "9", "2")
        .withCaching(cache)
        .useCachedValueWhenAvailable(useCache)
        .withWorkspace("ga-demo1")
      client.stream(job).block.output.mkString("")
    } catch {
      case NonFatal(e) => e.toString
    }
    println(output)

    Platform.currentTime - before

  }
  val times = timesPar.seq

  def report(x: Seq[Long]) = {
    if (x.isEmpty) "no results"
    else {
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
