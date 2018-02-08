package agora.rest.stream

import agora.flow.{AsConsumerQueue, BasePublisher, BaseSubscriber}
import agora.rest.ServerConfig
import agora.rest.client.StreamSubscriberWebsocketClient
import io.circe.Json

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
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

    val socketClientFuture                                                            = client.subscriptions.createSubscriber(name, PRNT)
    val socket: StreamSubscriberWebsocketClient[AsConsumerQueue.QueueArgs, PRNT.type] = Await.result(socketClientFuture, 10.seconds)

    val PUB: BasePublisher[String] = BasePublisher[String](20)

    val x = client.publishers.create[String, BasePublisher[String]](name, PUB)

    var line = StdIn.readLine("CHAT:")
    while (line != "quit") {
      PUB.publish(line)
      line = StdIn.readLine("CHAT:")
    }
    println("stopping...")

    println("done")
  }
}
