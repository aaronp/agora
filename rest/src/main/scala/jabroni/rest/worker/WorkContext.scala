package jabroni.rest
package worker

import java.nio.file.{Path, StandardOpenOption}

import akka.http.scaladsl.marshalling.{Marshal, ToResponseMarshaller}
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.{ContentType, ContentTypes, HttpEntity, HttpResponse}
import akka.http.scaladsl.server.RequestContext
import akka.http.scaladsl.util.FastFuture
import akka.stream.IOResult
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import io.circe.{Decoder, Encoder, Json}
import jabroni.api.SubscriptionKey
import jabroni.api.exchange.{Exchange, RequestWorkAck, WorkSubscription}
import jabroni.rest.multipart.{MultipartInfo, MultipartPieces}

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
case class WorkContext[T](exchange: Exchange,
                          subscriptionKey: Option[SubscriptionKey],
                          subscription: WorkSubscription,
                          requestContext: RequestContext,
                          request: T) {

  import requestContext._

  val matchDetails = MatchDetailsExtractor.unapply(requestContext.request)

  val resultPromise = Promise[HttpResponse]()

  def responseFuture = resultPromise.future


  def completeWithJson[A](value: A)(implicit enc: Encoder[A]) = {
    val response: Json = enc(value)
    val resp = Marshal(HttpEntity(`application/json`, response.noSpaces)).toResponseFor(requestContext.request)
    completeWith(resp)
  }

  def completeWithSource(dataSource: Source[ByteString, Any], contentType: ContentType = `application/octet-stream`) = {
    val entity = HttpEntity(contentType, dataSource)
    val resp = Marshal(entity).toResponseFor(requestContext.request)
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

  /** @return the multipart details for the given field (key), available only on multipart request inputs
    */
  def multipartForKey(key: String)(implicit ev: T =:= MultipartPieces): Option[(MultipartInfo, Source[ByteString, Any])] = {
    request.find {
      case (MultipartInfo(field, _, _), _) => field == key
    }
  }

  /** @return the multipart details for the given file name, available only on multipart request inputs
    */
  def multipartForFileName(fileName: String)(implicit ev: T =:= MultipartPieces) = {
    request.collectFirst {
      case pear@(MultipartInfo(_, Some(`fileName`), _), _) => pear
    }
  }

  /** @return the multipart source bytes for the given field (key), available only on multipart request inputs
    */
  def multipartSource(key: String)(implicit ev: T =:= MultipartPieces): Option[Source[ByteString, Any]] = {
    multipartForKey(key).map(_._2)
  }

  /** @return the multipart source bytes for the given field (key), available only on multipart request inputs
    */
  def multipartUpload(fileName: String)(implicit ev: T =:= MultipartPieces): Option[Source[ByteString, Any]] = {
    multipartForFileName(fileName).map(_._2)
  }

  def multipartSavedTo(key: String, path: Path, openOptions: java.nio.file.StandardOpenOption*)(implicit ev: T =:= MultipartPieces): Future[IOResult] = {
    val opt = multipartForKey(key).orElse(multipartForFileName(key)).map {
      case (_, src) =>
        src.runWith(FileIO.toPath(path, openOptions.toSet + StandardOpenOption.WRITE))
    }
    opt.getOrElse(Future.failed(new Exception(s"multipart doesn't exist for $key: ${multipartKeys}")))
  }

  def multipartJson(key: String)(implicit ev: T =:= MultipartPieces): Future[Json] = {
    multipartText(key).map { text =>
      io.circe.parser.parse(text) match {
        case Left(err) => throw new Exception(s"Error parsing part '$key' as json >>${text}<< : $err", err)
        case Right(json) => json
      }
    }
  }

  def multipartKey[A: Decoder : ClassTag] = implicitly[ClassTag[A]].runtimeClass.getName

  def multipartJson[A: Decoder : ClassTag](implicit ev: T =:= MultipartPieces): Future[A] = {
    val key = multipartKey
    val jsonFut = multipartJson(key)
    jsonFut.flatMap { json =>
      json.as[A] match {
        case Right(a) => FastFuture.successful(a)
        case Left(b) => FastFuture.failed(new Exception(s"Couldn't unmarshal '${key}' from '${json.noSpaces}' : $b", b))
      }
    }
  }

  def multipartText(key: String)(implicit ev: T =:= MultipartPieces): Future[String] = {
    val found = multipartSource(key)
    found.map(srcAsText).getOrElse(Future.failed(new Exception(s"Couldn't find '$key' in ${request.keySet}")))
  }

  def multipartKeys(implicit ev: T =:= MultipartPieces): Set[MultipartInfo] = request.keySet
}
