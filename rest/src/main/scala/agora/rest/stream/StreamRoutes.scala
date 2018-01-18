package agora.rest.stream

import agora.api.json.JsonSemigroup
import agora.flow.AsConsumerQueue.QueueArgs
import agora.flow.{AsConsumerQueue, BaseProcessor, HistoricProcessor, HistoricProcessorDao}
import agora.rest.exchange.ClientSubscriptionMessage
import akka.NotUsed
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.server.Directives.{extractMaterializer, handleWebSocketMessages, path, _}
import akka.http.scaladsl.server.{Route, ValidationRejection}
import akka.stream.scaladsl.Flow
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import io.circe.syntax._

/**
  * A PoC set of routes for creating publishers/subscribers over web sockets
  */
class StreamRoutes extends StrictLogging with FailFastCirceSupport {

  private object Lock

  private[stream] val state = StreamRoutesState()

  def routes: Route = publishRoutes ~ subscribeRoutes ~ snapshotRoute()

  def snapshotRoute() = get {
    path("rest" / "stream" / "snapshot" / Segment) { name =>
      val json: Json = state.snapshot(name)
      complete(json)
    }
  }

  def subscribeRoutes = {
    subscribeRawData() ~ subscribeRawDataTakeNext() ~ subscribeRawDataCancel() ~ subscribeRawDataList()
  }

  /**
    * @return control routes for normal REST (non web-socket) requests to force take next/cancel/list:
    */
  def publishRoutes = {
    publishRawData() ~ publishRawDataTakeNext() ~ publishRawDataCancel() ~ publishRawDataList()
  }

  /**
    * The inverse of the publish route.
    * Incoming data will be either 'take' or 'cancel' messages, and pushed data will be the subscription.
    *
    * This shows that the back-pressure from downstream subscribers going via a publisher is propagated
    *
    * {{{
    * data in -->
    *
    * <-- take next | cancel
    * }}}
    */
  def subscribeRawData(): Route = {
    path("rest" / "stream" / "subscribe" / Segment) { name =>
      parameter('maxCapacity.?, 'initialRequest.?, 'discardOverCapacity.?) { (maxCapacityOpt, initialRequestOpt, discardOpt) =>
        import AsConsumerQueue._
        //        implicit val jsg = JsonSemigroup

        val args: QueueArgs[Json] = {
          val maxCapacity: Option[Int]             = maxCapacityOpt.map(_.toInt)
          val discardOverCapacity: Option[Boolean] = discardOpt.map(_.toBoolean)
          QueueArgs[Json](maxCapacity, discardOverCapacity)(JsonSemigroup)
        }
        val initialRequest = initialRequestOpt.map(_.toInt).getOrElse(0)
        logger.debug(s"starting simple subscribe to $name w/ $args and initialRequest $initialRequest")

        extractMaterializer { implicit materializer =>
          val subscriberFlow: Flow[Message, Message, NotUsed] = Lock.synchronized {
            val republish: BaseProcessor[Json]       = BaseProcessor(args)
            val consumerFlow: DataConsumerFlow[Json] = new DataConsumerFlow[Json](name, republish, initialRequest)
            state.newSimpleSubscriber(consumerFlow)
          }

          handleWebSocketMessages(subscriberFlow)
        }
      }
    }
  }

  def subscribeRawDataTakeNext(): Route = {
    get {
      path("rest" / "stream" / "subscribe" / Segment / "request" / IntNumber) { (name, takeNextInt) =>
        Lock.synchronized {

          state.getSimpleSubscriber(name) match {
            case None =>
              val keys = state.subscriberKeys.mkString(",")
              complete(NotFound, s"Couldn't find $name, available subscribers are: ${keys}")
            case Some(found) =>
              logger.debug(s"$name subscriber taking $takeNextInt")
              found.foreach(_.takeNextFromRepublisher(takeNextInt))
              complete(found.size.asJson)
          }
        }
      }
    }
  }

  def subscribeRawDataCancel(): Route = {
    get {
      path("rest" / "stream" / "subscribe" / Segment / "cancel") { name =>
        Lock.synchronized {
          state.getUploadEntrypoint(name) match {
            case None =>
              val keys = state.subscriberKeys.mkString(",")
              complete(NotFound, s"Couldn't find $name, available subscribers are: ${keys}")
            case Some(sp) =>
              logger.debug(s"$name publisher cancelling")

              complete(sp.cancel().asJson)
          }
        }
      }
    }
  }

  def subscribeRawDataList(): Route = {
    get {
      (path("rest" / "stream" / "subscribe") & pathEnd) {
        val list = Lock.synchronized {
          state.subscriberKeys.toList.asJson
        }
        complete(list)
      }
    }
  }

  /**
    * Just publishes what's sent verbatim. This is the most basic publish route, demonstrating the
    * {{{
    * arbitrary data in -->
    *
    * <-- take next | cancel
    * }}}
    * handshake
    *
    * @return
    */
  def publishRawData(): Route = {
    path("rest" / "stream" / "publish" / Segment) { name =>
      parameter('maxCapacity.?, 'discardOverCapacity.?) { (maxCapacityOpt, discardOverCapacityOpt) =>
        implicit val jsonSemi = JsonSemigroup
        val newQueue: QueueArgs[Json] = {
          val maxCapacity         = maxCapacityOpt.map(_.toInt)
          val discardOverCapacity = discardOverCapacityOpt.map(_.toBoolean)
          QueueArgs[Json](maxCapacity, discardOverCapacity)
        }

        logger.debug(s"starting simple publish for $name w/ $newQueue")

        extractMaterializer { implicit materializer =>
          val publishFlowOpt = Lock.synchronized {
            state.getUploadEntrypoint(name) match {
              case None =>
                // TODO - we should have a single exec context specific for all historic publishers not tied
                // to the initial request
                import materializer.executionContext
                val clientSubscriptionMessagePublisher = HistoricProcessor(HistoricProcessorDao[ClientSubscriptionMessage]())
                val sp                                 = new DataUploadFlow[QueueArgs, Json](name, newQueue, clientSubscriptionMessagePublisher)
                Option(state.newUploadEntrypoint(sp))
              case Some(_) => None
            }
          }

          publishFlowOpt match {
            case Some(publishFlow) => handleWebSocketMessages(publishFlow)
            case None =>
              reject(ValidationRejection(s"Publisher '$name' already exists"))
          }
        }
      }
    }
  }

  def publishRawDataTakeNext(): Route = {
    get {
      path("rest" / "stream" / "publish" / Segment / "request" / IntNumber) { (name, takeNextInt) =>
        Lock.synchronized {
          state.getUploadEntrypoint(name) match {
            case None =>
              val keys = state.uploadKeys.mkString(",")
              complete(NotFound, s"Couldn't find $name, available publishers are: ${keys}")
            case Some(sp) =>
              logger.debug(s"$name publisher taking $takeNextInt")
              complete(sp.takeNext(takeNextInt).asJson)
          }
        }
      }
    }
  }

  def publishRawDataCancel(): Route = {
    get {
      path("rest" / "stream" / "publish" / Segment / "cancel") { name =>
        Lock.synchronized {
          state.getUploadEntrypoint(name) match {
            case None =>
              val keys = state.uploadKeys.mkString(",")
              complete(NotFound, s"Couldn't find $name, available publishers are: ${keys}")
            case Some(sp) =>
              logger.debug(s"$name publisher cancelling")
              complete(sp.cancel().asJson)
          }
        }
      }
    }
  }

  def publishRawDataList(): Route = {
    get {
      (path("rest" / "stream" / "publish") & pathEnd) {
        val list = Lock.synchronized {
          state.uploadKeys.toList.asJson
        }
        complete(list)
      }
    }
  }
}
