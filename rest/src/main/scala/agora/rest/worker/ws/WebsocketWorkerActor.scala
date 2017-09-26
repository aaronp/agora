package agora.rest.worker.ws

import agora.api.exchange.{ClientResponse, Exchange, WorkSubscription}
import agora.rest.worker.DynamicWorkerRoutes
import akka.actor.{Actor, ActorRef, ActorRefFactory, Props}
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json
import io.circe.generic.auto._

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

/**
  * Client to Worker: I'd like to open a work subscription XY
  *
  * Dynamic Worker to Exchange: create a thing at <client worker counter + 1>
  * Dynamic Worker to Client: OK - here's your ID
  * *
  *
  * Client to Worker: Please take x items on ID
  * DynamicWorker to Exchange: What he said
  * *
  *
  * Exchange to DynamicWorker: Do this job
  * DynamicWorker to Client: Do this job
  * *
  * Client to DynamicWorker: Submit this job
  * DynamicWorker to Client: Here's the result
  *
  * Client to DynamicWorker: Ha ha sucker! Thanks ... and here's the result of the job you asked me to do
  *
  */
class WebsocketWorkerActor(workerRoutes: DynamicWorkerRoutes)
    extends Actor
    with FailFastCirceSupport
    with StrictLogging {

  import WebsocketWorkerActor._
  implicit def executionContext = context.dispatcher

  private def exchange = workerRoutes.exchange

  override def unhandled(message: Any): Unit = {
    super.unhandled(message)
    sys.error(s"${getClass.getSimpleName}(s${self.path}) couldn't handle $message")
  }

  override def receive: Receive = onRequest(Nil)

  def onRequest(pendingMessages: List[ToWebsocketWorker]): Receive = {
    case WebsocketWorkerRequest(CreateSubscription(subscription), promise) =>
      workerRoutes.withSubscription(subscription).withInitialRequest(0).addHandler[Json] { ctxt =>
        ???
      }

    case WebsocketWorkerRequest(ResubmitRequest(job), promise) =>
      val future: Future[ClientResponse] = exchange.submit(job)
      future.onComplete {
        case Success(response) =>
        case Failure(err)      =>
      }
      ???
    case WebsocketWorkerRequest(TakeNext(n), promise) =>
      ???
    case WebsocketWorkerRequest(CompleteRequest(respJson), promise) =>
      val fut = ???
      promise.tryCompleteWith(fut)

  }
}

object WebsocketWorkerActor {

  case class WebsocketWorkerRequest(msg: FromWebsocketWorker, promise: Promise[List[ToWebsocketWorker]])

  def props(workerRoutes: DynamicWorkerRoutes) = Props(new WebsocketWorkerActor(workerRoutes))

  def apply(workerRoutes: DynamicWorkerRoutes)(implicit factory: ActorRefFactory): ActorRef = {
    factory.actorOf(props(workerRoutes))
  }

  def create(workerRoutes: DynamicWorkerRoutes) = {
    ???
  }
}
