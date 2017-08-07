package agora.rest.worker

import agora.api.exchange._
import agora.api.worker.SubscriptionKey
import agora.health.HealthDto
import akka.http.scaladsl.model.ContentTypes.`application/json`
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives.{path, _}
import akka.http.scaladsl.server.directives.BasicDirectives.extractRequestContext
import akka.http.scaladsl.server.{RequestContext, Route}
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller
import akka.stream.Materializer
import io.circe.Json

import scala.concurrent.Future
import scala.language.reflectiveCalls

object DynamicWorkerRoutes {
  def apply(subscription: WorkSubscription)(implicit mat: Materializer): DynamicWorkerRoutes = {
    implicit val predicate = JobPredicate()
    val exchange           = Exchange(MatchObserver())
    DynamicWorkerRoutes(exchange, subscription, 1)
  }
}

// see http://doc.akka.io/docs/akka-stream-and-http-experimental/1.0/scala/http/routing-dsl/index.html
case class DynamicWorkerRoutes(exchange: Exchange, defaultSubscription: WorkSubscription, defaultInitialRequest: Int)(implicit mat: Materializer) { self =>
  implicit val ec = mat.executionContext

  private object HandlerWriteLock

  // keeps track of all of the workers by a unique sub-path
  protected var workerByPath = Map[String, OnWork[_]]()

  // paths which can't be used
  private val ReservedPaths = Set("rest/health", "rest/subscriptions")

  /**
    * @return the REST route
    */
  def routes: Route = {
    workerRoutes ~ health
  }

  /**
    * Replace the handler subscription
    *
    * @param oldPath             the path key
    * @param newWorkSubscription the new subscription to use
    * @return true if the oldPath was a valid handler path
    */
  private[worker] def updateHandler(oldPath: String, newWorkSubscription: WorkSubscription): Boolean = {
    HandlerWriteLock.synchronized {
      val handlerOpt = workerByPath.get(oldPath)
      val opt = handlerOpt.map { (handler: OnWork[_]) =>
        val onWork = handler.withSubscription(newWorkSubscription)
        workerByPath = (workerByPath - oldPath).updated(newWorkSubscription.details.path, onWork)
      }
      opt.map(_ => true).getOrElse(false)
    }

  }

  def health = (get & path("rest" / "health") & pathEnd) {
    import io.circe.syntax._
    complete {
      HttpResponse(entity = HttpEntity(`application/json`, HealthDto().asJson.noSpaces))
    }
  }

  def listSubscriptions = (get & path("rest" / "subscriptions") & pathEnd) {
    complete {
      val paths = workerByPath.map {
        case (path, onWork) =>
          Json.obj(path -> onWork.subscription.details.aboutMe)
      }
      HttpResponse(entity = HttpEntity(ContentTypes.`application/json`, Json.arr(paths.toList: _*).noSpaces))
    }
  }

  def workerRoutes: Route = handleWorkRoute ~ listSubscriptions

  //  def handleWorkRoute: Route = pathPrefix("rest" / "worker") {
  def handleWorkRoute: Route = {
    post {
      path(Remaining) { remaining =>
        find(remaining) match {
          case None =>
            extractRequest { req =>
              complete(
                (StatusCodes.NotFound,
                 HttpEntity(s""" Invalid path: '${req.uri}'
                       |Known handlers include:
                       |${workerPaths.toList.sorted.mkString("\n")}""".stripMargin)))
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

  /**
    * Syntactic sugar to allow users to:
    * {{{
    *   val routes : WorkerRoutes = ...
    *   routes.usingSubscription(XYZ).addHandler( ctxt => ... )
    * }}}
    *
    * This is really only needed when a single worker has multiple handlers (i.e. work routes).
    *
    * The implicit parameter list to [[addHandler()]] takes a [[WorkSubscription]], which defaults to one provided
    * from the configuration.
    *
    * So, without the syntactic sugar, subsequent handlers would either need to set up another subscription in implicit
    * scope or explicitly provide the curried implicit paramter, something akin to:
    * {{{
    *       workRoute.addHandler[SomeCaseClass] { ctxt =>
    *         ...
    *       }(workRoute.defaultSubscription.withPath("myNewHandler"))
    * }}}
    *
    * @param f a function which modifies the default subscription (or completely ignores it)
    * @return A DSL-specific class which expects 'addHandler' to be called on it
    */
  def usingSubscription(f: WorkSubscription => WorkSubscription) = new WithSubscriptionWord(this, f, None)

  /**
    * Like 'usingSubscription', this function provides a DSL for adding a handler.
    *
    * @param s the work subscription used for the handler
    * @return A DSL-specific class which expects 'addHandler' to be called on it
    */
  def withSubscription(s: WorkSubscription) = usingSubscription(_ => s)

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
                                                   fromRequest: FromRequestUnmarshaller[T]): Future[RequestWorkAck] = {

    val path                                               = subscription.details.path
    val subscriptionAckFuture: Future[WorkSubscriptionAck] = exchange.subscribe(subscription)

    require(!ReservedPaths.contains(path), s"Path '$path' is reserved and so can't be used")

    val handler = new OnWork[T](subscription, fromRequest, onReq)

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

  /**
    * Replace the handler logic for the handler at the given path
    *
    * @param path        the relative path snippet for the handler to replace
    * @param onReq       the new handler logic
    * @param fromRequest the unmarshaller to use
    * @tparam T the handler body type
    * @return true if a handler was previously registered at the given path (and thus replaced)
    */
  def updateHandler[T](path: String)(onReq: WorkContext[T] => Unit)(fromRequest: FromRequestUnmarshaller[T]) = {
    HandlerWriteLock.synchronized {
      val replaced = workerByPath.get(path).map { oldHandler =>
        val newOnWork = new OnWork[T](oldHandler.subscription, fromRequest, onReq)
        workerByPath = workerByPath.updated(path, newOnWork)
        true
      }
      replaced.getOrElse(false)
    }
  }

  private def setSubscriptionKeyOnHandler(path: String, key: SubscriptionKey) = {
    HandlerWriteLock.synchronized {
      workerByPath.get(path).foreach { handler =>
        require(handler.key.isEmpty, s"Key '$key' was already chuffing set for '${path}'!?@?")
        handler.key = Option(key)
      }
    }
  }

  protected def find(workerName: String): Option[OnWork[_]] = HandlerWriteLock.synchronized {
    workerByPath.get(workerName)
  }

  /** @return the registered worker paths
    */
  def workerPaths: Set[String] = {
    HandlerWriteLock.synchronized {
      workerByPath.keySet
    }
  }

  /**
    * Captures the 'handler' logic for a subscription.
    */
  protected class OnWork[T](val subscription: WorkSubscription, unmarshaller: FromRequestUnmarshaller[T], onReq: WorkContext[T] => Unit) {
    def withSubscription(newSubscription: WorkSubscription) = {
      new OnWork[T](newSubscription, unmarshaller, onReq)
    }

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
        implicit val fre = unmarshaller
        val context      = WorkContext[T](exchange, self, key, subscription, ctxt, tea)
        onReq(context)
        context.responseFuture
      }
    }
  }

}
