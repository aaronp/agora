package agora.exec.rest.ws

import agora.api.exchange._
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, TextMessage}
import akka.http.scaladsl.server.Directives.{as, entity, handleWebSocketMessages, path, _}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Sink, Source}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport

import scala.language.reflectiveCalls

case class WebsocketWorkerRoutes() extends FailFastCirceSupport {

  val websocketRoute = post {

    path("rest" / "websocket" / "create") {
      extractMaterializer { implicit mat =>
        entity(as[WorkSubscription]) { newSubscription =>
          handleWebSocketMessages(greeter)
        }
      }
    }
  }

  //
  //  val greeterWebSocketService: Flow[Message, TextMessage, NotUsed] =
  //    Flow[Message]
  //      .mapConcat {
  //        // we match but don't actually consume the text message here,
  //        // rather we simply stream it back as the tail of the response
  //        // this means we might start sending the response even before the
  //        // end of the incoming message has been received
  //        case tm: TextMessage =>
  //          val requestText: Future[String] = tm.textStream.runReduce(_ ++ _)
  //          val str: Source[String, _]      = tm.textStream
  //          val resp: List[TextMessage]     = TextMessage(Source.single("Hello ") ++ tm.textStream) :: Nil
  //          resp
  //        case bm: BinaryMessage =>
  //          // ignore binary messages but drain content to avoid the stream being clogged
  //          bm.dataStream.runWith(Sink.ignore)
  //          Nil
  //      }
  //
  def greeter(implicit mat: Materializer): Flow[Message, Message, Any] =
    Flow[Message].mapConcat {
      case tm: TextMessage =>
        TextMessage(Source.single("Hello ") ++ tm.textStream ++ Source.single("!")) :: Nil
      case bm: BinaryMessage =>
        // ignore binary messages but drain content to avoid the stream being clogged
        bm.dataStream.runWith(Sink.ignore)
        Nil
    }

}

object WebsocketWorkerRoutes {

  //  object ClientHandlerState {
  //
  //    import io.circe.parser._
  //
  //    case class TakeWork(takeWork: Int)
  //
  //    object TakeWork {
  //      def unapply(msg: String): Option[TakeWork] = {
  //        decode[TakeWork](msg).right.toOption
  //      }
  //    }
  //
  //    object CreateWorkspace {
  //      def unapply(msg: String): Option[WorkSubscription] = {
  //        val either: Either[circe.Error, WorkSubscription] = decode[WorkSubscription](msg)
  //        either.right.toOption
  //      }
  //    }
  //
  //  }

  //
  //  case class ClientHandlerState(dw: DynamicWorkerRoutes) {
  //
  //    import ClientHandlerState._
  //
  //    def onCreateWorkspace(create: WorkSubscription): Future[Json] = {
  //      import io.circe.syntax._
  //      val ack: Future[RequestWorkAck] = dw.usingSubscription(_ => create).withInitialRequest(0).addHandler { ctxt =>
  //        ctxt.complete {
  //          ???
  //        }
  //        ???
  //      }
  //
  //      ack.map(_.asJson)
  //    }
  //
  //    def flow = Flow[Message].mapAsync {
  //      case tm: TextMessage =>
  //        val incomingMessageFuture: Future[String] = tm.textStream.reduce(_ ++ _).runWith(Sink.head)
  //        incomingMessageFuture.flatMap {
  //          case CreateWorkspace(create) =>
  //            onCreateWorkspace(create).map { json =>
  //              TextMessage(json.noSpaces)
  //            }
  //        }
  //
  //      case bm: BinaryMessage =>
  //        bm.dataStream.runWith(Sink.ignore)
  //    }
  //  }

  //  def addRoute(dw: DynamicWorkerRoutes, subscription: WorkSubscription, take: Int, onWork: Json => Future[(Json, Int)])(implicit mat: Materializer) = {
  //    import mat._
  //
  //    dw.usingSubscription(_ => subscription).withInitialRequest(take).addHandler { (ctxt: WorkContext[Json]) =>
  //      onWork(ctxt.request).onSuccess {
  //        case (resp, next) =>
  //          ctxt.completeWith(Future.successful(resp), next)
  //      }
  //    }
  //  }

}
