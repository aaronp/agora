package jabroni.rest
package worker

import akka.stream.Materializer
import akka.stream.scaladsl.Source
import akka.util.ByteString
import io.circe.Json
import jabroni.api.SubscriptionKey
import jabroni.api.`match`.MatchDetails
import jabroni.api.exchange.{Exchange, RequestWorkAck, WorkSubscription}
import jabroni.rest.multipart.{MultipartInfo, MultipartPieces}

import scala.concurrent.Future

case class WorkContext[T](exchange: Exchange, subscriptionKey: Option[SubscriptionKey], subscription: WorkSubscription, matchDetails: Option[MatchDetails], request: T) {

  def take(n: Int): Option[Future[RequestWorkAck]] = subscriptionKey.map(s => exchange.take(s, n))

  def details = subscription.details

  def path = details.path.get

  def multipartForKey(key: String)(implicit ev: T =:= MultipartPieces, mat: Materializer) = {
    request.find {
      case (MultipartInfo(field, _, _), _) => field == key
    }
  }

  def multipartForFileName(fileName: String)(implicit ev: T =:= MultipartPieces, mat: Materializer) = {
    request.collectFirst {
      case pear@(MultipartInfo(_, Some(`fileName`), _), _) => pear
    }
  }

  def multipartSource(key: String)(implicit ev: T =:= MultipartPieces, mat: Materializer): Option[Source[ByteString, Any]] = {
    multipartForKey(key).map(_._2)
  }

  def multipartUpload(fileName: String)(implicit ev: T =:= MultipartPieces, mat: Materializer): Option[Source[ByteString, Any]] = {
    multipartForFileName(fileName).map(_._2)
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
