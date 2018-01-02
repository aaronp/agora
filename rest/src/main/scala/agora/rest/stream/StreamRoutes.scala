package agora.rest.stream

import agora.api.streams.{BaseProcessor, ConsumerQueue}
import akka.NotUsed
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.server.Directives.{extractMaterializer, handleWebSocketMessages, path, _}
import akka.http.scaladsl.server.Route
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

  private val state = StreamRoutesState()

  def routes = publishRoutes ~ subscribeRoutes

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
        val maxCapacity = maxCapacityOpt.map(_.toInt)
        val discardOverCapacity = discardOpt.map(_.toBoolean)
        val initialRequest = initialRequestOpt.map(_.toInt).getOrElse(0)
        logger.debug(s"starting simple subscribe to $name w/ maxCapacity $maxCapacity, discard $discardOverCapacity and initialRequest $initialRequest")

        def newQueue = ConsumerQueue.jsonQueue(maxCapacity, discardOverCapacity)

        extractMaterializer { implicit materializer =>
          val subscriberFlow: Flow[Message, Message, NotUsed] = Lock.synchronized {
            val republish: BaseProcessor[Json] = BaseProcessor[Json](() => newQueue)
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
              val keys = state.simpleSubscriberByName.keySet.mkString(",")
              complete(NotFound, s"Couldn't find $name, available subscribers are: ${keys}")
            case Some(found) =>
              logger.debug(s"$name subscriber taking $takeNextInt")
              found.foreach(_.takeNext(takeNextInt))
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
              val keys = state.simpleSubscriberByName.keySet.mkString(",")
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
          state.simpleSubscriberByName.keySet.toList.asJson
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
      parameter('maxCapacity.?, 'initialRequest.?) { (maxCapacityOpt, initialRequestOpt) =>
        val maxCapacity = maxCapacityOpt.map(_.toInt).getOrElse(10)
        val initialRequest = initialRequestOpt.map(_.toInt).getOrElse(0)
        logger.debug(s"starting simple publish for $name w/ maxCapacity $maxCapacity and initialRequest $initialRequest")

        extractMaterializer { implicit materializer =>
          val publishFlow = Lock.synchronized {
            val sp = new DataUploadFlow[Json](name, maxCapacity, initialRequest)
            state.newUploadEntrypoint(sp)
          }
          handleWebSocketMessages(publishFlow)
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
              val keys = state.uploadEntrypointByName.keySet.mkString(",")
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
              val keys = state.uploadEntrypointByName.keySet.mkString(",")
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
          state.uploadEntrypointByName.keySet.toList.asJson
        }
        complete(list)
      }
    }
  }
}
