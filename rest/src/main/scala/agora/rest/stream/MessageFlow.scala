package agora.rest.stream

import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.{Done, NotUsed}
import com.typesafe.scalalogging.StrictLogging
import io.circe.parser._
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import org.reactivestreams.{Publisher, Subscriber}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Constructs a message flow based on a publisher of [T] values and a text message handler
  */
object MessageFlow extends StrictLogging {

  def apply[A: Decoder, B: Encoder](publisher: Publisher[B], handler: Subscriber[A])(implicit mat: Materializer): Flow[Message, Message, NotUsed] = {

    val msgHandler: Sink[Message, NotUsed] = {
      val incomingHandler: Sink[A, NotUsed] = Sink.fromSubscriber(handler)
      incomingHandler.contramap[Message] {
        case TextMessage.Strict(json) =>
          decode[A](json) match {
            case Right(value) => value
            case Left(err)    => throw err
          }
        case other => sys.error(s"Expected strict text messages in the websocket exchange, but encountered: $other")
      }
    }

    val source = Source.fromPublisher(publisher).map { next =>
      TextMessage(next.asJson.noSpaces)
    }

    Flow.fromSinkAndSource(msgHandler, source)
  }

  def apply[T: Encoder](publisherFuture: Future[Publisher[T]])(onClientMessage: String => Unit)(implicit ec: ExecutionContext,
                                                                                                mat: Materializer): Flow[Message, Message, NotUsed] = {

    val incomingHandler: Sink[Message, Future[Done]] = Sink.foreach[Message] {
      case TextMessage.Strict(json) => onClientMessage(json)
      case other                    => sys.error(s"Expected strict text messages in the websocket exchange, but encountered: $other")
    }

    val source = Source.fromFuture(publisherFuture).flatMapConcat { publisher =>
      Source.fromPublisher(publisher).map { next: T =>
        TextMessage(next.asJson.noSpaces)
      }
    }

    Flow.fromSinkAndSource(incomingHandler, source)
  }
}
