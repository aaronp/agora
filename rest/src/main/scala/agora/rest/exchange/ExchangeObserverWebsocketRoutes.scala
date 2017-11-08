package agora.rest.exchange

import javax.ws.rs.Path

import agora.api.exchange.ServerSideExchange
import agora.api.exchange.observer.{ExchangeNotificationMessage, SingleSubscriptionExchangePublisher}
import agora.rest.exchange.ActorExchange.ActorExchangeClient
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.http.scaladsl.server.Directives.{handleWebSocketMessages, path, _}
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.{Done, NotUsed}
import com.typesafe.scalalogging.{LazyLogging, StrictLogging}
import io.circe.syntax._
import io.swagger.annotations.{ApiOperation, ApiResponse, ApiResponses}

import scala.concurrent.{ExecutionContext, Future}

/**
  * Websocket routes for observing the exchange state
  */
trait ExchangeObserverWebsocketRoutes extends StrictLogging {

  def exchange: ServerSideExchange

  def maxCapacity: Int = Int.MaxValue

  @Path("/rest/exchange/observe")
  @ApiOperation(
    value = "connects a websocket to the exchange to observer exchange events",
    httpMethod = "GET"
  )
  @ApiResponses(
    Array(
      new ApiResponse(code = 101, message = "ExchangeNotificationMessage messages", response = classOf[ExchangeNotificationMessage])
    ))
  def observe: Route = {
    path("observe") {
      extractMaterializer { implicit materializer =>
        extractExecutionContext { implicit ec =>
          logger.debug("Adding a websocket observer")
          val publisherFuture: Future[SingleSubscriptionExchangePublisher] = {
            exchange.underlying match {
              case threadSafeExchange: ActorExchangeClient =>
                threadSafeExchange.withQueueState() { queueState =>
                  val publisher = SingleSubscriptionExchangePublisher(maxCapacity, queueState)
                  exchange.observer += publisher
                  publisher
                }
              case _ =>
                logger.warn("We can't perform a thread-safe subscription")
                val publisher = SingleSubscriptionExchangePublisher(maxCapacity)
                Future.successful(publisher)
            }
          }

          val flow = ExchangeObserverWebsocketRoutes.serverFlow(publisherFuture)
          handleWebSocketMessages(flow)
        }
      }
    }
  }

}

object ExchangeObserverWebsocketRoutes extends LazyLogging {

  def serverFlow(publisherFuture: Future[SingleSubscriptionExchangePublisher])(implicit ec: ExecutionContext,
                                                                               mat: Materializer): Flow[Message, Message, NotUsed] = {

    val cancelSink: Sink[Message, Future[Done]] = Sink.foreach[Message] {
      case TextMessage.Strict(json) =>
        logger.debug(s"Received client message:\n$json\n")
        ClientSubscriptionMessage.unapply(json).foreach {
          case TakeNext(n) =>
            publisherFuture.foreach { publisher =>
              publisher.subscription.foreach { sub =>
                sub.request(n)
              }
            }
          case Cancel =>
            publisherFuture.foreach { publisher =>
              publisher.subscription.foreach { sub =>
                sub.cancel()
              }
            }
        }

      case other => sys.error(s"Expected strict text messages in the websocket exchange, but encountered: $other")
    }

    val source = Source.fromFuture(publisherFuture).flatMapConcat { publisher =>
      Source.fromPublisher(publisher).map { notification: ExchangeNotificationMessage =>
        val msg: Message = TextMessage(notification.asJson.noSpaces)
        msg
      }
    }

    Flow.fromSinkAndSource(cancelSink, source)
  }

}
