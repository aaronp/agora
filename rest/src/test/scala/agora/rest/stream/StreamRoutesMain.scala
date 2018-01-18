package agora.rest.stream

import agora.rest.{RunningService, ServerConfig}

import scala.concurrent.Future
import scala.io.StdIn

object StreamRoutesMain {

  def main(a: Array[String]): Unit = {

    val serverConfig = ServerConfig("host=localhost", "port=7777", "akka.http.client.idle-timeout=9min")
    import serverConfig.serverImplicits._

    val sr                                                         = new StreamRoutes
    val future: Future[RunningService[ServerConfig, StreamRoutes]] = RunningService.start[ServerConfig, StreamRoutes](serverConfig, sr.routes, sr)
    future.onComplete { res =>
      println("Done w/ " + res)

    }

    println("Hit summat to stop")
    StdIn.readLine()
    println("stopping...")
    future.foreach { svc =>
      svc.stop()
    }
    println("done")
  }
}
