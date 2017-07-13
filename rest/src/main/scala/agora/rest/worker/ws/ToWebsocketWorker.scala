package agora.rest.worker.ws

import agora.api.exchange.{QueueStateResponse, SubmitJob}
import io.circe.generic.auto._
import io.circe.{Decoder, Encoder, Json}

/**
  * Messages sent to the websocket
  */
sealed trait ToWebsocketWorker

object ToWebsocketWorker {

  implicit val JsonDecoder: Decoder[ToWebsocketWorker] = {
    (implicitly[Decoder[QueueState]]
      .map(x => x: ToWebsocketWorker))
      .or(implicitly[Decoder[OnJob]].map(x => x: ToWebsocketWorker))
      .or(implicitly[Decoder[OnResubmitResponse]].map(x => x: ToWebsocketWorker))
  }

  implicit object JsonEncoder extends Encoder[ToWebsocketWorker] {
    override def apply(a: ToWebsocketWorker): Json = a match {
      case msg: QueueState         => implicitly[Encoder[QueueState]].apply(msg)
      case msg: OnJob              => implicitly[Encoder[OnJob]].apply(msg)
      case msg: OnResubmitResponse => implicitly[Encoder[OnResubmitResponse]].apply(msg)
    }
  }

  def unapply(json: String): Option[ToWebsocketWorker] = {
    import io.circe.parser._
    decode[ToWebsocketWorker](json).right.toOption
  }
}

case class QueueState(queueState: QueueStateResponse) extends ToWebsocketWorker

case class OnJob(job: SubmitJob) extends ToWebsocketWorker

case class OnResubmitResponse(response: String) extends ToWebsocketWorker
