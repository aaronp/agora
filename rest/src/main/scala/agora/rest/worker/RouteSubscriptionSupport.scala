package agora.rest.worker

import agora.api.`match`.MatchDetails
import agora.api.exchange.{Exchange, RequestWorkAck}
import agora.rest.MatchDetailsExtractor
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.RouteResult.Complete
import akka.http.scaladsl.server._
import akka.http.scaladsl.server.directives.BasicDirectives
import akka.util.ByteString
import com.typesafe.scalalogging.LazyLogging

import scala.concurrent.Future
import scala.util.control.NonFatal

/**
  * The 'RouteSubscriptionSupport' exposes a directive exists to integrate existing routes with an [[Exchange]].
  *
  * Consider some basic route:
  * {{{
  *   post(path("foo")) { ... }
  * }}}
  *
  * We want to be able to integrate it with an exchange to make it a reactive, work-pulling route. We also want that to
  * be non-intrusive. A client of our basic route shouldn't be forced to go via the [[Exchange]] to use our endpoint;
  * it should work the same with or without the exchange.
  *
  * To accomplish this, we'll need to know:
  * 1) The exchange from which to pull more work
  * 2) the subscription key used to request the next work item
  * 3) a means to identify whether the given request was made as a result of a match on the exchange (as opposed to
  * just being hit directly)
  * 4) to know when the response is complete
  *
  *
  */
trait RouteSubscriptionSupport extends LazyLogging {

  import akka.http.scaladsl.server.directives.BasicDirectives._

  /**
    * Represents how many work items will be requested upon request completion
    */
  protected sealed trait TakeAction

  /** This is the default case -- just request one work item (e.g. one in, one out semantics)
    */
  case object ReplaceOne extends TakeAction

  /**
    * Tries subscribe to the 'optimal' requests. So if we handle a request
    * which tells us there are 3 items remaining and 'optimal' is set to 10,
    * then a 'take' request is made for 7 work items.
    *
    * If 'optimal' is less than the items remaining then no action is performed.
    * @param optimal
    */
  case class SetPendingTarget(optimal: Int) extends TakeAction

  case class CustomOnCompleteAction(onComplete: (MatchDetails, Exchange) => Int) extends TakeAction

  def requestIsFromExchange(originalRequest: HttpRequest): Boolean = {
    MatchDetailsExtractor.unapply(originalRequest).isDefined
  }

  def extractMatchDetails: Directive1[Option[MatchDetails]] = {
    BasicDirectives.extractRequest.map(MatchDetailsExtractor.unapply)
  }

  /**
    * A directive which will ask the exchange for another work item if the request was sent in response to an
    * exchange match (e.g. it contains the exchanger headers).
    *
    * The subscription key used for the exchange will be taken from the [[MatchDetails]] taken from the
    * request. If there are no [[MatchDetails]] then 'takeNextOnComplete' will have no effect
    *
    * @param exchange the exchange from which the work is pulled
    * @param action   how many items should be requested on complete? Defaults to just replacing one work item
    * @return a directive yo
    */
  def takeNextOnComplete(exchange: Exchange, action: TakeAction = ReplaceOne): Directive[Unit] = {

    extractMatchDetails.tflatMap {
      case Tuple1(Some(md)) => requestOnComplete(md, exchange, action)
      case Tuple1(None)     => BasicDirectives.pass
    }
  }

  def takeNext(matchDetails: MatchDetails, exchange: Exchange, action: TakeAction = ReplaceOne): Future[RequestWorkAck] = {
    try {
      val nrToTake = action match {
        case ReplaceOne => 1
        case SetPendingTarget(optimal) =>
          optimal - matchDetails.remainingItems
        case CustomOnCompleteAction(custom) =>
          custom(matchDetails, exchange)
      }
      logger.debug(s"$action has instructed to take $nrToTake for $matchDetails")
      if (nrToTake > 0) {
        exchange.take(matchDetails.subscriptionKey, nrToTake)
      } else {
        Future.successful(RequestWorkAck(matchDetails.subscriptionKey, nrToTake))
      }
    } catch {
      case NonFatal(e) =>
        logger.error(s"Error requesting next $action for $matchDetails from $exchange: $e", e)
        Future.failed(e)
    }
  }

  def requestOnComplete(matchDetails: MatchDetails, exchange: Exchange, action: TakeAction = ReplaceOne): Directive0 = {
    mapRouteResultPF {
      case Complete(resp) =>
        val flow = agora.io.OnComplete.onUpstreamComplete[ByteString, ByteString] { _ =>
          takeNext(matchDetails, exchange, action)
        }

        val newEntity = resp.entity.transformDataBytes(flow)
        Complete(resp.withEntity(newEntity))
    }
  }
}
