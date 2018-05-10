package lupin.example

import lupin.{Publishers, Subscribers}
import lupin.pub.collate.CollatingPublisher
import lupin.pub.sequenced.{DurableProcessor, DurableProcessorInstance}
import lupin.sub.BaseSubscriber
import org.reactivestreams.{Publisher, Subscription}

import scala.collection.immutable
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, ExecutionContext, Future}

/**
  * This class encapsulates the union of some data updates ([[FieldFeed]]s) with a [[ViewPort]] feed.
  *
  * The [[FieldFeed]]s should be common across the whole application -- it could be distributed, clustered, HA,
  * whatever. The only requirement is that they can accept new subscribers with and [[IndexSelection]], meaning
  * get all the data for that Field between two indices.
  *
  * The ViewPort is specific to one user's view of the data (e.g. someone scrolling through the data).
  *
  * Updates of the ViewPort will update the subscriptions to the FieldFeeds, e.g. requesting new or different
  * index ranges or adding/removing feeds from fields.
  *
  * //TODO - a future implementation consideration would be to make the 'availableFieldsByName' a stream
  * // as well -- one which can fold over some Query object to check if there are any new fields available.
  *
  * To consider a typical use-case, consider a scrolling window similar to google maps, or infinite scroll.
  * A subscription may be made for the 'user.name' field, indices 0 to 100. The user may then scroll down in the UI,
  * where now they'll want rows 95 - 105 on the screen. As there's already a request for 0-100 fields, we could:
  * 1) cancel and remake the subscription centres on the current view
  * 2) add a new subscription for fields 100 - 200
  *
  * It would seem #2 would be easier/more efficient. If we decoupled the logic of adding subscriptions from that of
  * what should be removed, that will make a putting a sliding window over the data easier.
  *
  * If we had index windows such as:
  *
  *
  * {{{
  +-----------------------------------------------------------+ rows  1 - 10
  |                                                           |
  |                                                           |
  |                                                           |
  |                                                           |
  |                                                           |
  +-----------------------------------------------------------+ rows 11 - 20
  |                                                           |
  |                                                           |
  |         +----- user view or rows 24 - 43 ------+          |
  |         |--------------------------------------|          |                  We could have index ranges of
  |         |--------------------------------------|          |                  some fixed size.
  +-----------------------------------------------------------+ rows 21 - 30     We could then request range updates
  |         |--------------------------------------|          |                  for the intersection of the view port
  |         |--------------------------------------|          |                  with these blocks, perhaps +/- one to
  |         |--------------------------------------|          |                  support look ahead/behind
  |         |--------------------------------------|          |
  |         |--------------------------------------|          |
  +-----------------------------------------------------------+ rows 31 - 40
  |         |--------------------------------------|          |
  |         |--------------------------------------|          |
  |         +--------------------------------------+          |
  |                                                           |
  |                                                           |
  +-----------------------------------------------------------+ rows 41 - 50
  |                                                           |
  |                                                           |
  |                                                           |
  |                                                           |
  |                                                           |
  +-----------------------------------------------------------+ rows 51 - 60

}}}
  *
  *
  *
  * To make this work, we'll need the following types of publisher/subscribers:
  *
  * 1) SINGLE FIELD FEED: field feeds which can provide data and then updates based on an index range
  * 2) VIEW PORT: a view port feed which will drop updates except for the most recent view.
  *    for example, if the user is scrolling down quickly, we should have requested 1 ViewPort element,
  *    and then processed it by updating the fields.
  *    The updates which have occurred while scrolling should overwrite each other until we then request
  *    one more ViewPort after having processed the first request.
  *
  *    Alternatively, if we want to simulate some kind of "velocity" or "crazy jumpy user" scenario,
  *    the ViewPort feed could be (hidden from us) joined w/ some ticking publisher and then only
  *    propagating updates which are stable or overlap.
  * 3) CELL FEED: A publisher which can join N other feeds of the same type. This can have different
  *               characteristics, like interleaving elements, fairness, etc. I think for now we'll just
  *               be concerned w/ ensuring 'request' calls from downstream subscribers get made to all
  *               upstream publishers.
  *
  * For the view updates themselves, they'll just need to work out a diff between two ViewPorts based on
  * columns requested and index range intersections.
  *
  *
  * @param availableFieldsByName
  * @param initialSubscribedFieldsByName
  * @param ec
  * @param timeout
  * @tparam ID
  */
class ViewState[ID] private (availableFieldsByName: Map[String, FieldFeed[ID]], initialSubscribedFieldsByName: Map[FieldAndRange, Subscription] = Map.empty)(
    implicit ec: ExecutionContext,
    timeout: FiniteDuration) {

  // exposes a publisher/subscriber for ViewPort updates
  val viewSubscription = DurableProcessor[ViewPort]()

  private val viewUpdateListener = BaseSubscriber[ViewPort](1) {
    case (s, viewPort) =>
      updateSubscriptionsBasedOnViewPort(viewPort)
      s.request(1)
  }

  viewUpdateListener.request(1)
  viewSubscription.subscribe(viewUpdateListener)

  // exposes a publisher of 'CellUpdate[ID, FieldUpdate[ID]]' -- notifications when
  // a 'cell' is updated in the current ViewPort
  val cellPublisher: CollatingPublisher[FieldAndRange, CellUpdate[ID, FieldUpdate[ID]]] = {
    CollatingPublisher[FieldAndRange, CellUpdate[ID, FieldUpdate[ID]]](fair = true)
  }

  var subscribedFieldsByName: Map[FieldAndRange, Subscription] = initialSubscribedFieldsByName

  private[example] def updateSubscriptionsBasedOnViewPort(currentView: ViewPort)(implicit timeout: FiniteDuration): ViewState[ID] = {

    // TODO - we also have to know what feed ranges we need to update/change.
    // if the indices moved from e.g. (0 - 100) to (10 - 110), we can create a new subscription for e.g. (100 - 200)
    // as well as re-using the (0 - 100) one.
    // Alternatively we could completely cancel/resubscribe for the new indices.
    // or get even more complicated and introduce a specific indices subscription.

    //
    // 1) cancel subscriptions to fields which we no longer care about
    //
    // decide what new subscriptions to make/keep, and which index ranges to filter for each field feed
    //
    val (keepFields: Map[FieldAndRange, Subscription], cancelFields) = subscribedFieldsByName.partition {
      case (fieldAndCol, s) =>
        //TODO -  also add to the criteria index ranges which we no longer care about
        currentView.cols.contains(fieldAndCol.field)
    }
    cancelFields.foreach {
      case (field, subscription) =>
        cellPublisher.cancelSubscriber(field)
        subscription.cancel()
    }

    //
    // 2) Subscribe to new field feeds
    //
    val requiredNewSubscriptions: List[String] = currentView.cols.filterNot { field: String =>
      ???
      keepFields.exists(_._1.field == field)
    }

    val futureSubscriptions: immutable.Iterable[(String, Publisher[CellUpdate[ID, FieldUpdate[ID]]], Future[Subscription])] = availableFieldsByName.collect {
      case (fieldName, fieldPublisher) if requiredNewSubscriptions.contains(fieldName) =>
        // subscribe from the given indices
        val newFieldSubscription = DurableProcessor[FieldUpdate[ID]]()
        fieldPublisher.subscribeWith(currentView.indices, newFieldSubscription)

        val cellFeed: Publisher[CellUpdate[ID, FieldUpdate[ID]]] = Publishers.map(newFieldSubscription) { next: FieldUpdate[ID] =>
          asCellUpdate(fieldName, currentView, next, None)
        }

        val fieldAndRange: FieldAndRange = ??? //()
        cellFeed.subscribe(cellPublisher.newSubscriber(fieldAndRange))

        (fieldName, cellFeed, newFieldSubscription.subscriptionFuture)
    }

    //
    // connect the new cell publishers with the existing cell publisher
    //
    val newCellPublisher: Publisher[CellUpdate[ID, FieldUpdate[ID]]] = Publishers.combine[CellUpdate[ID, FieldUpdate[ID]]] {
      futureSubscriptions.map {
        case (_, pub, _) => pub
      }
    }
    //cellPublisher.switchSubscriptionTo(newCellPublisher)

    val newSubscriptions = futureSubscriptions.map {
      case (field, _, future) => field -> Await.result(future, timeout)
    }.toMap

    //      val newSubscribed = keepFields ++ newSubscriptions
    //      copy(subscribedFieldsByName = newSubscribed)
    ???
  }

  def asCellUpdate(field: String, currentView: ViewPort, fieldUpdate: FieldUpdate[ID], previouslySentSeqNo: Option[SeqNo]): CellUpdate[ID, FieldUpdate[ID]] = {
    val cellUpdate: CellUpdate[ID, FieldUpdate[ID]] =
      CellUpdate(previouslySentSeqNo, fieldUpdate.seqNo, Map(CellCoord(fieldUpdate.index, field) -> fieldUpdate))
    cellUpdate
  }
}

object ViewState {
  def subscribeTo[ID](viewPort: Publisher[ViewPort], availableFieldsByName: Map[String, FieldFeed[ID]])(implicit ec: ExecutionContext,
                                                                                                        timeout: FiniteDuration) = {

    val state = new ViewState[ID](availableFieldsByName, Map.empty)
    viewPort.subscribe(state.viewSubscription)
    state.cellPublisher
  }
}
