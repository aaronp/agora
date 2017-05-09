package jabroni.rest
package worker

import akka.stream.Materializer
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

  def source(key: String)(implicit ev: T =:= MultipartPieces, mat: Materializer) = {
    val pieces: MultipartPieces = request
    pieces.collect {
      case (MultipartInfo(`key`, _, _), value) => value
    }.headOption
  }

  def textPart(key: String)(implicit ev: T =:= MultipartPieces, mat: Materializer): Future[String] = {
    val found = source(key)
    found.map(srcAsText).getOrElse(Future.failed(new Exception(s"Couldn't find '$key' in ${request.keySet}")))
  }
  def multipartKeys(implicit ev: T =:= MultipartPieces) = {
    request.keySet
  }
}
