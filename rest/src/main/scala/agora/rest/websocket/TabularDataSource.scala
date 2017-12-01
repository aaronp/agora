package agora.rest.websocket

import agora.api.streams.{BasePublisher, BaseSubscriber, ConsumerQueue}
import akka.NotUsed
import akka.http.scaladsl.model.ws.Message
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import org.reactivestreams.Subscriber

import scala.concurrent.Future

case class ColumnSort(columns: String, ascending: Boolean)

case class SortCriteria(columns: List[ColumnSort] = Nil)

case class ViewPortCriteria[C](firstRowIndex: Int, numberOfRows: Int, columns: List[String], sort: SortCriteria, filter: C)

trait TabularDataSource[CRITERIA, DELTAS] {

  def deltas(query: ViewPortCriteria[CRITERIA]): Future[DELTAS]
}

object MarketFeed {

  case class Value(price: Double, quantity: Int)

  case class PriceUpdate(ric: String, valuesByName: Map[String, Value])

  case class Delta(property: String, oldValue: Option[Any], newValue: Option[Any])

  case class Deltas(ric: String, deltas: List[Delta]) {
    def update(key: String, oldValue: Option[Any], newValue: Option[Any]): Deltas = {
      copy(deltas = Delta(key, oldValue, newValue) :: deltas)
    }
  }

  val feedFromExchange = BasePublisher[PriceUpdate](1000)
  val deltaPublisher   = BasePublisher[Deltas](1000)

  val marketView = new BaseSubscriber[PriceUpdate](10) {
    private var valuesByRic = Map[String, Map[String, Any]]()

    private object DeltaPublisher extends BasePublisher[Deltas] {
      override def newQueue() = ConsumerQueue(10)
      override protected def newSubscription(subscriber: Subscriber[_ >: Deltas]) = {
        val s = super.newSubscription(subscriber)
        val sow =
          s.onElement(sow)
        s
      }
    }

    override def onNext(update: PriceUpdate) = {

      val oldValues = valuesByRic.getOrElse(update.ric, Map.empty)
      val (newMap, deltas) = update.valuesByName.foldLeft(oldValues -> Deltas(update.ric, Nil)) {
        case (entry @ (map, deltas), (key, newValue)) =>
          oldValues.get(key) match {
            case Some(oldValue) if oldValue == newValue => entry
            case oldValue =>
              map.updated(key, newValue) -> deltas.update(key, oldValue, Option(newValue))
          }
      }
      valuesByRic = valuesByRic.updated(update.ric, newMap)
      deltaPublisher.publish(deltas)
    }

  }

  feedFromExchange.subscribe(marketView)

}

object InfiniteInts {

  case class IntCriteria(fromY: Int, toY: Int, time: Long)

  case class Coords(row: Int, column: String)

  case class IntDelta(key: Coords, lastValue: Int, newValue: Int, tick: Long)

  object IntStreamExample {

    sealed trait ClientResponseMessage

    case class IntDelta(at: Coords, oldValue: Int, newValue: Int, tick: Long)

    case class IntView(deltas: List[IntDelta]) extends ClientResponseMessage

    sealed trait ClientMessage

    case class ViewportUpdate(criteria: ViewPortCriteria[IntCriteria]) extends ClientMessage

    case class TakeNext(take: Int, toTick: Long) extends ClientMessage

    def computeDeltas(criteriaBefore: Option[ViewPortCriteria[IntCriteria]], criteriaAfter: Option[ViewPortCriteria[IntCriteria]], tick: Long) = {}

    def flow(implicit mat: Materializer): Flow[Message, Message, NotUsed] = {
      // global publisher for all ints/ticks. This will just publish on demand for
      // each subscriber
      val publisher = BasePublisher[ClientResponseMessage](100)

      val subscriber = new BaseSubscriber[ClientMessage](1) {
        var lastCriteriaSent: Option[ViewPortCriteria[IntCriteria]] = None

        var currentCriteria: Option[ViewPortCriteria[IntCriteria]] = None

        override def onNext(t: ClientMessage) = {

          t match {
            case ViewportUpdate(newCriteria) =>
              currentCriteria = Option(newCriteria)
            case TakeNext(n, tick) =>
              val result = computeDeltas(lastCriteriaSent, currentCriteria, tick)
              lastCriteriaSent = currentCriteria

              request(n)
          }
        }
      }
      MessageFlow(publisher, subscriber)
    }
  }

  object Source extends TabularDataSource[IntCriteria, IntDelta] {
    override def deltas(query: ViewPortCriteria[IntCriteria]): Future[List[IntDelta]] = {

      val xValues = query.firstRowIndex.to(query.firstRowIndex + query.numberOfRows)

      Future.successful(List())
    }
  }

}
