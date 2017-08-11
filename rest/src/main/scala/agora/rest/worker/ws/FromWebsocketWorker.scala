package agora.rest.worker.ws

import agora.api.exchange.{SubmitJob, WorkSubscription}
import io.circe.generic.auto._
import io.circe.{Decoder, DecodingFailure, Encoder, Json}

/**
  * these are messages sent from the websocket client (e.g. a browser)
  */
sealed trait FromWebsocketWorker

object FromWebsocketWorker {

  implicit val JsonDecoder: Decoder[FromWebsocketWorker] = {

    // format: off
    (implicitly[Decoder[TakeNext]].map(x => x: FromWebsocketWorker))
      .or(implicitly[Decoder[CreateSubscription]].map(x => x: FromWebsocketWorker))
      .or(implicitly[Decoder[CompleteRequest]].map(x => x: FromWebsocketWorker))
      .or(implicitly[Decoder[ResubmitRequest]].map(x => x: FromWebsocketWorker))
    // format: on
  }

  implicit object JsonEncoder extends Encoder[FromWebsocketWorker] {
    override def apply(a: FromWebsocketWorker): Json = a match {
      case msg: TakeNext           => implicitly[Encoder[TakeNext]].apply(msg)
      case msg: CreateSubscription => implicitly[Encoder[CreateSubscription]].apply(msg)
      case msg: CompleteRequest    => implicitly[Encoder[CompleteRequest]].apply(msg)
      case msg: ResubmitRequest    => implicitly[Encoder[ResubmitRequest]].apply(msg)
    }
  }

  def unapply(json: String): Option[FromWebsocketWorker] = {
    import io.circe.parser._
    decode[FromWebsocketWorker](json).right.toOption
  }
}

/**
  * request to take more work
  */
case class TakeNext(request: Int) extends FromWebsocketWorker

case class CreateSubscription(subscription: WorkSubscription) extends FromWebsocketWorker

/**
  * reply to the original incoming request
  */
case class CompleteRequest(response: Json) extends FromWebsocketWorker

/**
  * submit the given job to the exchange, presumably to fulfill an 'around' AOP type workflow
  */
case class ResubmitRequest(submit: SubmitJob) extends FromWebsocketWorker
