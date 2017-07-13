package agora.rest.worker.ws

import akka.NotUsed
import akka.http.scaladsl.model.ws.BinaryMessage
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, ExecutionContext, Future}

trait WebsocketWorker {

  def onMessage(from: FromWebsocketWorker): Unit

  /**
    * submit the given job to the websocket
    *
    * @param job
    */
  def send(job: ToWebsocketWorker): Unit
}

object WebsocketWorker {

  /**
    * The
    *
    * @param worker
    * @param mat
    */
  class Instance(worker: WebsocketWorker, unmarshalTimeout: FiniteDuration)(implicit mat: Materializer) extends StrictLogging {

    import akka.http.scaladsl.model.ws.{Message, TextMessage}
    import akka.stream.scaladsl.Flow

    /**
      * The client sent a json message
      *
      * @param json
      */
    def onJsonMessage(json: String) = {}

    def flow: Flow[Message, TextMessage, NotUsed] = {

      import mat._

//      Flow[Message].vi

      Flow[Message]
        .mapConcat {
          case inputMessage: TextMessage =>
            val fut: Future[List[ToWebsocketWorker]] = inputMessage.textStream.runWith(Sink.head).flatMap {
              case FromWebsocketWorker(msg) =>
                worker.onMessage(msg)
                ???
              case other =>
                val msg = s"Couldn't interpret web socket text message '${other}'"
                logger.error(msg)
                Future.failed(new Exception(msg))
            }

            val responses: List[ToWebsocketWorker] = Await.result(fut, unmarshalTimeout)
            responses.map { (resp: ToWebsocketWorker) =>
              import io.circe.syntax._
              import io.circe.generic.auto._
              TextMessage(resp.asJson.noSpaces)
            }

            // TODO - implement protocol
            Nil

          case bm: BinaryMessage =>
            logger.info("Ignoring binary message")
            // ignore binary messages but drain content to avoid the stream being clogged
            bm.dataStream.runWith(Sink.ignore)
            Nil
        }
    }
  }

}
