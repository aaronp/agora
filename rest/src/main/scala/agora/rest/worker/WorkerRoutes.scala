package agora.rest.worker

import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{path, _}
import akka.http.scaladsl.server.directives.BasicDirectives.extractRequestContext
import akka.http.scaladsl.server.{RequestContext, Route}
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream.Materializer
import io.circe.Json
import agora.api.exchange._
import agora.api.worker.SubscriptionKey
import agora.health.HealthDto

import scala.concurrent.Future
import scala.language.reflectiveCalls

object WorkerRoutes {
  def apply()(implicit mat: Materializer): WorkerRoutes = {
    implicit val predicate = JobPredicate()
    val exchange           = Exchange(MatchObserver())
    WorkerRoutes(exchange, WorkSubscription(), 1)
  }
}

// see http://doc.akka.io/docs/akka-stream-and-http-experimental/1.0/scala/http/routing-dsl/index.html
case class WorkerRoutes(exchange: Exchange, defaultSubscription: WorkSubscription, defaultInitialRequest: Int)(implicit mat: Materializer) extends MultipartHandlerSupport {

  implicit val ec = mat.executionContext

  private object HandlerWriteLock

  protected var workerByPath = Map[String, OnWork[_]]()

  def routes: Route = {
    workerRoutes ~ multipartRoutes ~ health
  }

  def health = (get & path("rest" / "health") & pathEnd) {
    import io.circe.syntax._
    complete {
      HttpResponse(entity = HttpEntity(`application/json`, HealthDto().asJson.noSpaces))
    }
  }

  def listSubscriptions = get {
    pathPrefix("rest") {
      path("subscriptions") {
        complete {
          val paths = workerByPath.map {
            case (path, onWork) =>
              Json.obj(path -> onWork.subscription.details.aboutMe)
          }
          val multipartPaths = multipartByPath.map {
            case (path, onWork) =>
              Json.obj(path -> onWork.subscription.details.aboutMe)
          }
          val pathsList = paths.toSeq ++ multipartPaths.toSeq
          HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, Json.arr(pathsList: _*).noSpaces))
        }
      }
    }
  }

  def workerRoutes: Route = handleWorkRoute ~ listSubscriptions

  def handleWorkRoute: Route = pathPrefix("rest" / "worker") {
    post {
      path(Remaining) { remaining =>
        find(remaining) match {
          case None =>
            extractRequest { req =>
              complete(
                (StatusCodes.NotFound,
                 HttpEntity(s""" Invalid path: ${req.uri}
                       |
                   |Known handlers include:
                       |  ${workerByPath.keySet.toList.sorted.mkString("\n")}""".stripMargin)))
            }
          case Some(worker) =>
            extractRequestContext { ctxt =>
              complete {
                worker.handle(ctxt)
              }
            }
        }
      }
    }
  }

  def usingSubscription(f: WorkSubscription => WorkSubscription) = new WithSubscriptionWord(this, f)

  /**
    * The main body for a handler ... registers a function ('onReq') which does some work.
    *
    * Instead of a thinking of a generic computation as a function from A => B, This exposes
    * the function as WorkContext[A] => ResponseEntity
    *
    * The WorkContext exposes a handle onto the exchange (so the computation can request more work)
    * and access details about the work sent to it
    *
    * @param onReq          the compute functions
    * @param subscription   the subscription to use when asking for work for this computation
    * @param initialRequest how many work items to initially ask for
    * @tparam T the request input type
    * @return a future of the 'request work' ack
    */
  def addHandler[T](onReq: WorkContext[T] => Unit)(implicit subscription: WorkSubscription = defaultSubscription,
                                                   initialRequest: Int = defaultInitialRequest,
                                                   fromRequest: Unmarshaller[HttpRequest, T]): Future[RequestWorkAck] = {

    val path                                               = subscription.details.path.getOrElse(sys.error(s"The subscription doesn't contain a path: ${subscription.details}"))
    val subscriptionAckFuture: Future[WorkSubscriptionAck] = exchange.subscribe(subscription)

    val handler = new OnWork[T](subscription, initialRequest, fromRequest, onReq)

    HandlerWriteLock.synchronized {
      workerByPath.get(path).foreach { oldHandler =>
        // we'd have to consider this use case and presumably cancel the old subscription
        sys.error(s"Attempt to re-register a worker at '$path'. Replacing handlers isn't supported: $oldHandler")
      }
      workerByPath = workerByPath.updated(path, handler)
    }

    subscriptionAckFuture.flatMap { ack =>
      // update our handler with it's subscription key (see 'OnWork' comment for why it has to be created first,
      // before we have this subscription key)
      setSubscriptionKeyOnHandler(path, ack.id)

      // ask for some initial work
      if (initialRequest > 0) {
        exchange.take(ack.id, initialRequest)
      } else {
        Future.successful(RequestWorkAck(ack.id, initialRequest))
      }
    }
  }

  private def setSubscriptionKeyOnHandler(path: String, key: SubscriptionKey) = {
    HandlerWriteLock.synchronized {
      workerByPath.get(path).foreach { handler =>
        require(handler.key.isEmpty, "Key was already chuffing set!?@?")
        handler.key = Option(key)
      }
    }
  }

  protected def find(workerName: String): Option[OnWork[_]] = workerByPath.get(workerName)

  /**
    * Captures the 'handler' logic for a subscription.
    */
  protected class OnWork[T](val subscription: WorkSubscription, initialRequest: Int, unmarshaller: Unmarshaller[HttpRequest, T], onReq: WorkContext[T] => Unit) {

    /**
      * we have the case where a worker can actually handle a request at any time from a client ... even before we bother
      * subscribing to the exchange.
      *
      * This isn't even a race condition ... there's nothing to say requests to a worker 'microservice' (ahem) has to
      * have gone through our exchange at all.
      *
      * Hence, the subscription key is optional
      */
    var key: Option[SubscriptionKey] = None

    def handle(ctxt: RequestContext): Future[HttpResponse] = {
      unmarshaller(ctxt.request).flatMap { tea =>
        val context = WorkContext(exchange, key, subscription, ctxt, tea)
        onReq(context)
        context.responseFuture
      }
    }
  }

}
