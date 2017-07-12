package agora.rest
package worker

import akka.http.scaladsl.marshalling.{Marshal, ToResponseMarshaller}
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.RequestContext
import akka.http.scaladsl.util.FastFuture
import akka.stream.scaladsl.Source
import akka.util.ByteString
import io.circe.{Decoder, Encoder, Json}
import agora.api.SubscriptionKey
import agora.api.`match`.MatchDetails
import agora.api.exchange.{WorkSubscription, _}
import agora.api.worker.WorkerDetails
import agora.rest.multipart.MultipartFormImplicits._
import agora.rest.multipart.MultipartInfo
import akka.http.scaladsl.unmarshalling.FromRequestUnmarshaller

import scala.collection.immutable
import scala.concurrent.{Future, Promise}
import scala.reflect.ClassTag
import scala.util.control.NonFatal

/**
  * Wraps the input to a computation, allowing the computation (mostly) to call 'request(n)' so it can request more work.
  *
  * This class is analogous to a
  * [[http://www.reactive-streams.org/reactive-streams-1.0.0-javadoc/org/reactivestreams/Subscription.html Subscription]]
  * in [[http://www.reactive-streams.org Reactive Streams]]
  *
  * @param exchange       the interface to an exchange so it can request more work or even cancel the subscription or return the job
  * @param routes         the worker routes containing this handler
  * @param subscription   the details of the subscription
  * @param requestContext the http request context which contains the original HttpRequest, etc
  * @param request        the unmarshalled handler input
  * @tparam T the request type
  */
case class WorkContext[T: FromRequestUnmarshaller](exchange: Exchange,
                                                   routes: WorkerRoutes,
                                                   subscriptionKey: Option[SubscriptionKey],
                                                   subscription: WorkSubscription,
                                                   requestContext: RequestContext,
                                                   request: T) {

  import requestContext._

  /**
    * The match details associated with this request invocation. As the endpoint is just a normal endpoint which could
    * be invoked directly w/o having been redirected from an [[Exchange]], it could be empty.
    *
    * The MatchDetails are provided by values taken from the Http Headers.
    */
  val matchDetails: Option[MatchDetails] = MatchDetailsExtractor.unapply(requestContext.request)

  // the HttpResponse promise
  private val resultPromise = Promise[HttpResponse]()

  def responseFuture = resultPromise.future

  /** Replace the current handler logic with the new handler
    *
    * @param newHandler
    * @return
    */
  def become(newHandler: WorkContext[T] => Unit) = {
    routes.updateHandler(path)(newHandler)(implicitly[FromRequestUnmarshaller[T]])
  }

  /**
    * Replace the current subscription, effectively cancelling/creating a new subscription.
    *
    * @note The new subscription will be created, but no initial work items will be requested
    * @param newSubscription the new subscription
    * @return the updated work context w/ the new work subscription
    */
  def replaceSubscription(newSubscription: WorkSubscription): Future[WorkContext[T]] = {
    routes.updateHandler(subscription.details.path.get, newSubscription)

    val newAck: Future[WorkSubscriptionAck] = subscriptionKey match {
      case Some(id) =>
        exchange.cancelSubscriptions(id).flatMap { _ =>
          exchange.subscribe(newSubscription)
        }
      case None => exchange.subscribe(newSubscription)
    }

    newAck.map { ack =>
      copy(subscriptionKey = Option(ack.id))
    }
  }

  /**
    * Convenience function for 'replaceSubscription' which operates on the current subscription
    *
    * @param f the update subscription
    * @return the updated context with the new subscription
    */
  def updateSubscription(f: WorkSubscription => WorkSubscription): Future[WorkContext[T]] = {
    replaceSubscription(f(subscription))
  }

  /**
    * complete with the json value
    *
    * @param value the value to complete
    * @param enc
    * @tparam A
    * @return this context (builder pattern)
    */
  def completeWithJson[A](value: => A)(implicit enc: Encoder[A]): WorkContext[T] = completeWith {
    val response: Json = enc(value)
    val resp = Marshal(HttpEntity(`application/json`, response.noSpaces)).toResponseFor(requestContext.request)
    resp
  }

  /**
    * A convenience method to complete (return a result and request a new work item) with a byte source
    *
    * @param dataSource
    * @param contentType
    * @return
    */
  def completeWithSource(dataSource: Source[ByteString, Any], contentType: ContentType = `application/octet-stream`, numberToRequest: Int = 1) = {
    val entity = HttpEntity(contentType, dataSource)
    val resp = Marshal(entity).toResponseFor(requestContext.request)
    completeWith(resp, numberToRequest)
  }

  /**
    * Completing the request means sending a response back to the client and requesting another work item.
    *
    * The inner 'complete' thunk is lazy, so another work item will be requested even if the given lazily-evaluated
    * return value throws an exception.
    *
    * @param compute the lazy value to return, and upon the completion of which a new work item will be requested
    * @tparam A
    * @return this context (builder pattern)
    */
  def complete[A: ToResponseMarshaller](compute: => A): WorkContext[T] = {
    completeWith(asResponse(compute))
  }

  def asResponse[A: ToResponseMarshaller](compute: => A): Future[HttpResponse] = {
    try {
      val result: A = compute
      Marshal(result).toResponseFor(requestContext.request)
    } catch {
      case NonFatal(e) => FastFuture.failed(e)
    }
  }

  /**
    * By calling 'completeWith', it assures that another work item will be requested when this future completes
    * (whether successfully or not)
    *
    * @param respFuture the response with which will be marshalled back to the client (and upon who's completion a new work item will be requested)
    * @tparam A the response type
    * @return this context (builder pattern)
    */
  def completeWith[A: ToResponseMarshaller](respFuture: Future[A], numberToRequest: Int = 1): WorkContext[T] = {
    lazy val takeNext: Option[Future[RequestWorkAck]] = numberToRequest match {
      case 0 => None
      case n => request(n)
    }
    respFuture.onComplete { _ =>
      takeNext
    }
    val httpResp: Future[HttpResponse] = Marshal(respFuture).to[HttpResponse]
    resultPromise.completeWith(httpResp)
    this
  }

  /**
    * @param n the number of work items to request (typically 1, as we take one for each one we compute)
    * @return the Ack (if we indeed had a subscription key)
    */
  def request(n: Int): Option[Future[RequestWorkAck]] = subscriptionKey.map(s => exchange.take(s, n))

  /** @return the subscription details
    */
  def details: WorkerDetails = subscription.details

  /** @return the
    */
  def path: String = details.path.get


  /*
___  ___      _ _   _                  _    ___  ___     _   _               _
|  \/  |     | | | (_)                | |   |  \/  |    | | | |             | |
| .  . |_   _| | |_ _ _ __   __ _ _ __| |_  | .  . | ___| |_| |__   ___   __| |___
| |\/| | | | | | __| | '_ \ / _` | '__| __| | |\/| |/ _ \ __| '_ \ / _ \ / _` / __|
| |  | | |_| | | |_| | |_) | (_| | |  | |_  | |  | |  __/ |_| | | | (_) | (_| \__ \
\_|  |_/\__,_|_|\__|_| .__/ \__,_|_|   \__| \_|  |_/\___|\__|_| |_|\___/ \__,_|___/
                     | |
                     |_|
   */

  /** @return a value 'A' for each multipart info/source pair to which this partial function applies
    */
  def foreachMultipart[A](f: PartialFunction[(MultipartInfo, Source[ByteString, Any]), A])(implicit ev: T =:= Multipart.FormData): Future[immutable.Seq[A]] = {
    mapMultipart(f)
  }

  def mapMultipart[A](f: PartialFunction[(MultipartInfo, Source[ByteString, Any]), A])(implicit ev: T =:= Multipart.FormData): Future[immutable.Seq[A]] = {
    val fd: Multipart.FormData = request
    fd.mapMultipart(f)
  }

  def flatMapMultipart[A](f: PartialFunction[(MultipartInfo, Source[ByteString, Any]), Future[A]])(implicit ev: T =:= Multipart.FormData): Future[immutable.Seq[A]] = {
    val fd: Multipart.FormData = request
    val futures: Future[List[Future[A]]] = fd.mapMultipart(f)
    futures.flatMap { list =>
      Future.sequence(list)
    }
  }

  def mapFirstMultipart[A](f: PartialFunction[(MultipartInfo, Source[ByteString, Any]), A])(implicit ev: T =:= Multipart.FormData): Future[A] = {
    val fd: Multipart.FormData = request
    fd.mapFirstMultipart(f)
  }
}

object WorkContext {
  def multipartKey[A: Decoder : ClassTag] = implicitly[ClassTag[A]].runtimeClass.getName
}
