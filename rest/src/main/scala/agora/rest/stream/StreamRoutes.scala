package agora.rest.stream

import agora.json.JsonSemigroup
import agora.flow.AsConsumerQueue.QueueArgs
import agora.flow.{AsConsumerQueue, DurableProcessorDao}
import agora.rest.ui.UIRoutes
import agora.rest.{RunningService, ServerConfig}
import akka.NotUsed
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.server.Directives.{extractMaterializer, handleWebSocketMessages, path, _}
import akka.http.scaladsl.server.{Route, ValidationRejection}
import akka.stream.scaladsl.Flow
import com.typesafe.config.ConfigFactory
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

        val args: QueueArgs[Json] = {
          val maxCapacity: Option[Int]             = maxCapacityOpt.map(_.toInt)
          val discardOverCapacity: Option[Boolean] = discardOpt.map(_.toBoolean)
          QueueArgs[Json](maxCapacity, discardOverCapacity)(JsonSemigroup)
        }
        val initialRequest = initialRequestOpt.map(_.toInt).getOrElse(0)
        logger.debug(s"starting simple subscribe to $name w/ $args and initialRequest $initialRequest")

        extractMaterializer { implicit materializer =>
          val subscriberFlowOpt = Lock.synchronized {

            // TODO - use a specific dao execution context
            state.newSimpleSubscriber(name)
          }

          subscriberFlowOpt match {
            case Some(subscriberFlow) => handleWebSocketMessages(subscriberFlow)
            case None                 => reject(ValidationRejection(s"No publisher exists with name '$name'", None))
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
              val keys = state.subscriberKeys.mkString(",")
              complete(NotFound, s"Couldn't find $name, available subscribers are: ${keys}")
            case Some(found) =>
              logger.debug(s"$name subscriber taking $takeNextInt")
              found.foreach { x: SocketPipeline.DataPublisher[Json] =>
                x.takeNext(takeNextInt)
              }
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
          val publishFlowOpt: Option[Flow[Message, Message, NotUsed]] = Lock.synchronized {
            state.getUploadEntrypoint(name) match {
              case None =>
                // TODO - use a consistent/specific processor day, configurable max capacity default
                import materializer.executionContext

                val dao = DurableProcessorDao[Json](newQueue.maxCapacity.getOrElse(100))

                Option(state.newUploadEntrypoint(name, dao))
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

object StreamRoutes extends StrictLogging {
  def start(conf: ServerConfig = ServerConfig(ConfigFactory.load("agora-defaults.conf"))) = {
    val sr = new StreamRoutes

    val routes: Route = UIRoutes.unapply(conf).fold(sr.routes)(_.routes ~ sr.routes)

    logger.info(s"Starting demo on ${conf.location} w/ UI paths under ${conf.defaultUIPath}")
    RunningService.start(conf, routes, sr)
  }
}
