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
import agora.api.exchange.{Exchange, RequestWorkAck, WorkSubscription}
import agora.rest.multipart.MultipartFormImplicits._
import agora.rest.multipart.MultipartInfo

import scala.collection.immutable
import scala.concurrent.{Future, Promise}
import scala.reflect.ClassTag
import scala.util.control.NonFatal

/**
  * Wraps the input to a computation, allowing the computation (mostly) to call 'take(n)' so it can request more work
  *
  * @param exchange     the interface to an exchange so it can request more work or even cancel the subscription or return the job
  * @param subscription the details of the subscription
  * @param request      the job input
  * @tparam T the request type
  */
case class WorkContext[T](exchange: Exchange, subscriptionKey: Option[SubscriptionKey], subscription: WorkSubscription, requestContext: RequestContext, request: T) {

  import requestContext._

  val matchDetails: Option[MatchDetails] = MatchDetailsExtractor.unapply(requestContext.request)

  val resultPromise = Promise[HttpResponse]()

  def responseFuture = resultPromise.future

  def completeWithJson[A](value: A)(implicit enc: Encoder[A]) = {
    val response: Json = enc(value)
    val resp           = Marshal(HttpEntity(`application/json`, response.noSpaces)).toResponseFor(requestContext.request)
    completeWith(resp)
  }

  def completeWithSource(dataSource: Source[ByteString, Any], contentType: ContentType = `application/octet-stream`) = {
    val entity = HttpEntity(contentType, dataSource)
    val resp   = Marshal(entity).toResponseFor(requestContext.request)
    completeWith(resp)
  }

  def complete[T: ToResponseMarshaller](compute: => T) = {
    val respFuture: Future[HttpResponse] = try {
      val result: T = compute
      Marshal(result).toResponseFor(requestContext.request)
    } catch {
      case NonFatal(e) => FastFuture.failed(e)
    }
    completeWith(respFuture)
  }

  def completeWith(respFuture: Future[HttpResponse]) = {
    lazy val takeNext = request(1)
    respFuture.onComplete { _ =>
      takeNext
    }
    resultPromise.completeWith(respFuture)
  }

  /**
    * @param n the number of work items to request (typically 1, as we take one for each one we compute)
    * @return the Ack (if we indeed had a subscription key)
    */
  def request(n: Int): Option[Future[RequestWorkAck]] = subscriptionKey.map(s => exchange.take(s, n))

  def details = subscription.details

  def path = details.path.get

  def foreachMultipart[A](f: PartialFunction[(MultipartInfo, Source[ByteString, Any]), A])(implicit ev: T =:= Multipart.FormData): Future[immutable.Seq[A]] = {
    mapMultipart(f)
  }

  def mapMultipart[A](f: PartialFunction[(MultipartInfo, Source[ByteString, Any]), A])(implicit ev: T =:= Multipart.FormData): Future[immutable.Seq[A]] = {
    val fd: Multipart.FormData = request
    fd.mapMultipart(f)
  }

  def flatMapMultipart[A](f: PartialFunction[(MultipartInfo, Source[ByteString, Any]), Future[A]])(implicit ev: T =:= Multipart.FormData): Future[immutable.Seq[A]] = {
    val fd: Multipart.FormData           = request
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
  def multipartKey[A: Decoder: ClassTag] = implicitly[ClassTag[A]].runtimeClass.getName
}
