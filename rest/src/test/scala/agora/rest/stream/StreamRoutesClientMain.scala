package agora.rest.stream

import agora.flow.BaseSubscriber
import agora.rest.ServerConfig
import io.circe.Json

import scala.concurrent.Future
import scala.io.StdIn

object StreamRoutesClientMain {

  def main(a: Array[String]): Unit = {

    val serverConfig = ServerConfig("host=localhost", "port=7777")

    val name = "dave"

    val client = StreamRoutesClient(serverConfig.clientConfig)
    import serverConfig.serverImplicits._

    object PRNT extends BaseSubscriber[Json] {
      override def onNext(t: Json): Unit = {

        println(s"\n${t.spaces4}\n")
        val fut: Future[Json] = client.snapshot(name)
        fut.foreach { snap =>
          val str = snap.spaces4.lines.map(line => s"\t|  $line").mkString("\n============== snapshot ==============\n", "\n", "\n")
          println(str)
        }
        subscription().request(1)
      }
    }
    PRNT.request(1)
    client.subscriptions.createSubscriber(name, PRNT)

    println("Hit summat to stop")
    StdIn.readLine()
    println("stopping...")

    println("done")
  }
}
