package agora.rest.stream

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives.{extractMaterializer, handleWebSocketMessages, path, _}
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.StrictLogging
import io.circe.Json
import io.circe.syntax._

/**
  * A PoC set of routes for creating publishers/subscribers over web sockets
  */
class StreamRoutes extends StrictLogging {

  private object Lock

  private val state = StreamRoutesState()

  def routes = publishRoutes ~ subscribeRoutes

  def subscribeRoutes = {
    subscribeRawData() ~ subscribeRawDataTakeNext() ~ subscribeRawDataCancel() ~ subscribeRawDataList()
  }

  def publishRoutes = {
    publishRawData() ~ publishRawDataTakeNext() ~ publishRawDataCancel() ~ publishRawDataList()
  }

  /**
    * The inverse of the publish route.
    * Incoming data will be either 'take' or 'cancel' messages, and pushed data will be the subscription.
    *
    * This shows that the back-pressure from downstream subscribers going via a publisher is propogated
    *
    * {{{
    * data in -->
    *
    * <-- take next | cancel
    * }}}
    */
  def subscribeRawData(): Route = {
    path("rest" / "stream" / "subscribe" / Segment) { name =>
      parameter('maxCapacity.?, 'initialRequest.?) { (maxCapacityOpt, initialRequestOpt) =>
        val maxCapacity    = maxCapacityOpt.map(_.toInt).getOrElse(10)
        val initialRequest = initialRequestOpt.map(_.toInt).getOrElse(0)
        logger.debug(s"starting simple subscribe to $name w/ maxCapacity $maxCapacity and initialRequest $initialRequest")

        extractMaterializer { implicit materializer =>
          Lock.synchronized {

            val sp             = new DataConsumerFlow[Json](name, maxCapacity, initialRequest)
            val subscriberFlow = state.newSimpleSubscriber(sp)
            handleWebSocketMessages(subscriberFlow)
          }
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
              complete(found.size.asJson.noSpaces)
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

              complete(sp.cancel().asJson.noSpaces)
          }
        }
      }
    }
  }

  def subscribeRawDataList(): Route = {
    get {
      (path("rest" / "stream" / "subscribe") & pathEnd) {
        val list = Lock.synchronized {
          state.simpleSubscriberByName.keySet.toList.asJson.noSpaces
        }
        complete(list)
      }
    }
  }

  /**
    * Just publishes what's sent verbatim. This is the most basic publish route, demonstrating the
    * {{{
    * data in -->
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
        val maxCapacity    = maxCapacityOpt.map(_.toInt).getOrElse(10)
        val initialRequest = initialRequestOpt.map(_.toInt).getOrElse(0)
        logger.debug(s"starting simple publish for $name w/ maxCapacity $maxCapacity and initialRequest $initialRequest")

        extractMaterializer { implicit materializer =>
          Lock.synchronized {

            val sp          = new DataUploadFlow[Json](name, maxCapacity, initialRequest)
            val publishFlow = state.newUploadEntrypoint(sp)
            handleWebSocketMessages(publishFlow)
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
              val keys = state.uploadEntrypointByName.keySet.mkString(",")
              complete(NotFound, s"Couldn't find $name, available publishers are: ${keys}")
            case Some(sp) =>
              logger.debug(s"$name publisher taking $takeNextInt")
              complete(sp.takeNext(takeNextInt).asJson.noSpaces)
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

              complete(sp.cancel().asJson.noSpaces)
          }
        }
      }
    }
  }

  def publishRawDataList(): Route = {
    get {
      (path("rest" / "stream" / "publish") & pathEnd) {
        val list = Lock.synchronized {
          state.uploadEntrypointByName.keySet.toList.asJson.noSpaces
        }
        complete(list)
      }
    }
  }

}
