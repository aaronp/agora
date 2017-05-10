package jabroni.rest
package worker

import java.nio.file.{Path, StandardOpenOption}

import akka.http.scaladsl.server.RequestContext
import akka.stream.{IOResult, Materializer}
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString
import io.circe.Json
import jabroni.api.SubscriptionKey
import jabroni.api.`match`.MatchDetails
import jabroni.api.exchange.{Exchange, RequestWorkAck, WorkSubscription}
import jabroni.rest.multipart.{MultipartInfo, MultipartPieces}

import scala.concurrent.Future

/**
  * Wraps the input to a computation, allowing the computation (mostly) to call 'take(n)' so it can request more work
  *
  * @param exchange     the interface to an exchange so it can request more work or even cancel the subscription or return the job
  * @param subscription the details of the subscription
  * @param matchDetails the details of the job match (e.g. IDs, the subscription key, etc)
  * @param request      the job input
  * @tparam T the request type
  */
case class WorkContext[T](exchange: Exchange,
                          subscriptionKey: Option[SubscriptionKey],
                          subscription: WorkSubscription,
                          matchDetails: Option[MatchDetails],
                          request: T) {

  /**
    * @param n the number of work items to request (typically 1, as we take one for each one we compute)
    * @return the Ack (if we indeed had a subscription key)
    */
  def take(n: Int): Option[Future[RequestWorkAck]] = subscriptionKey.map(s => exchange.take(s, n))

  def details = subscription.details

  def path = details.path.get

  /** @return the multipart details for the given field (key), available only on multipart request inputs
    */
  def multipartForKey(key: String)(implicit ev: T =:= MultipartPieces, mat: Materializer): Option[(MultipartInfo, Source[ByteString, Any])] = {
    request.find {
      case (MultipartInfo(field, _, _), _) => field == key
    }
  }

  /** @return the multipart details for the given file name, available only on multipart request inputs
    */
  def multipartForFileName(fileName: String)(implicit ev: T =:= MultipartPieces, mat: Materializer) = {
    request.collectFirst {
      case pear@(MultipartInfo(_, Some(`fileName`), _), _) => pear
    }
  }

  /** @return the multipart source bytes for the given field (key), available only on multipart request inputs
    */
  def multipartSource(key: String)(implicit ev: T =:= MultipartPieces, mat: Materializer): Option[Source[ByteString, Any]] = {
    multipartForKey(key).map(_._2)
  }

  /** @return the multipart source bytes for the given field (key), available only on multipart request inputs
    */
  def multipartUpload(fileName: String)(implicit ev: T =:= MultipartPieces, mat: Materializer): Option[Source[ByteString, Any]] = {
    multipartForFileName(fileName).map(_._2)
  }

  def multipartSavedTo(key: String, path: Path, openOptions: java.nio.file.StandardOpenOption*)(implicit ev: T =:= MultipartPieces, mat: Materializer): Future[IOResult] = {
    val opt = multipartForKey(key).orElse(multipartForFileName(key)).map {
      case (_, src) =>
        src.runWith(FileIO.toPath(path, openOptions.toSet + StandardOpenOption.WRITE))
    }
    opt.getOrElse(Future.failed(new Exception(s"multipart doesn't exist for $key: ${multipartKeys}")))
  }

  def multipartJson(key: String)(implicit ev: T =:= MultipartPieces, mat: Materializer): Future[Json] = {
    import mat._
    multipartText(key).map { text =>
      io.circe.parser.parse(text) match {
        case Left(err) => throw new Exception(s"Error parsing part '$key' as json >>${text}<< : $err", err)
        case Right(json) => json
      }
    }
  }

  def multipartText(key: String)(implicit ev: T =:= MultipartPieces, mat: Materializer): Future[String] = {
    val found = multipartSource(key)
    found.map(srcAsText).getOrElse(Future.failed(new Exception(s"Couldn't find '$key' in ${request.keySet}")))
  }

  def multipartKeys(implicit ev: T =:= MultipartPieces) = {
    request.keySet
  }
}
