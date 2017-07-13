package agora.rest.worker.ws

import agora.api.exchange.SubmitJob
import io.circe.generic.auto._
import io.circe.{Decoder, DecodingFailure, Encoder, Json}

/**
  * these are messages sent from the websocket client (e.g. a browser)
  */
sealed trait FromWebsocketWorker

object FromWebsocketWorker {

  implicit val JsonDecoder: Decoder[FromWebsocketWorker] = {

    (implicitly[Decoder[Request]]
      .map(x => x: FromWebsocketWorker))
      .or(implicitly[Decoder[CompleteRequest]].map(x => x: FromWebsocketWorker))
      .or(implicitly[Decoder[ResubmitRequest]].map(x => x: FromWebsocketWorker))
  }

  implicit object JsonEncoder extends Encoder[FromWebsocketWorker] {
    override def apply(a: FromWebsocketWorker): Json = a match {
      case msg: Request         => implicitly[Encoder[Request]].apply(msg)
      case RefreshState         => RefreshState.JsonForm
      case msg: CompleteRequest => implicitly[Encoder[CompleteRequest]].apply(msg)
      case msg: ResubmitRequest => implicitly[Encoder[ResubmitRequest]].apply(msg)
    }
  }

  def unapply(json: String): Option[FromWebsocketWorker] = {
    import io.circe.parser._
    decode[FromWebsocketWorker](json).right.toOption
  }
}

// request to take more work
case class Request(n: Int) extends FromWebsocketWorker

// request to (manually) refresh the exchange state
case object RefreshState extends FromWebsocketWorker {
  val JsonForm = Json.fromString("refreshState")

  implicit object encoder extends Encoder[RefreshState.type] {
    override def apply(a: RefreshState.type): Json = JsonForm
  }

  implicit val decoder: Decoder[RefreshState.type] = Decoder.instance { cursor =>
    cursor.value.asString match {
      case Some("refreshState") => Right(RefreshState)
      case _                    => Left(DecodingFailure(s"Expected 'refreshState' but got '${cursor.value}'", cursor.history))
    }
  }
}

// reply to the original incoming request
case class CompleteRequest(response: Json) extends FromWebsocketWorker

// submit the given job to the exchange, presumably to fulfill an 'around' AOP type workflow
case class ResubmitRequest(submit: SubmitJob) extends FromWebsocketWorker
