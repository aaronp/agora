package agora.flow.impl

import agora.flow.{DurableProcessorReader, HasName}
import com.typesafe.scalalogging.StrictLogging
import org.reactivestreams.{Subscriber, Subscription}

import scala.concurrent.ExecutionContext
import scala.util.{Success, Try}

class DurableSubscription[T](publisher: DurableProcessorInstance[T],
                             initialRequestedIndex: Long,
                             val subscriber: Subscriber[_ >: T],
                             execContext: ExecutionContext,
                             queueCapacity: Int = 100)
  extends Subscription
    with HasName
    with StrictLogging {

  private val state = {
    val dao: DurableProcessorReader[T] = publisher.dao
    new SubscriberState[T](subscriber, dao, initialRequestedIndex)
  }
  private val subscriptionApi = SubscriberState.Api(state, queueCapacity)(execContext)

  override def name = subscriber match {
    case hn: HasName => hn.name
    case _ => toString
  }

  private def handleResponse(response: Try[SubscriberStateCommandResult]) = {
    response match {
      case Success(StopResult(_)) => publisher.removeSubscriber(this)
      case _ =>
    }
  }

  private[impl] def notifyComplete(idx: Long): Unit = {
    subscriptionApi.onComplete(idx).onComplete(handleResponse)(execContext)
  }

  private[impl] def notifyError(err: Throwable): Unit = {
    subscriptionApi.onError(err).onComplete(handleResponse)(execContext)
  }

  /** @param newIndex the new index available
    */
  private[impl] def onNewIndex(newIndex: Long) = {
    subscriptionApi.onNewIndexAvailable(newIndex).onComplete(handleResponse)(execContext)
  }

  override def cancel(): Unit = {
    subscriptionApi.onCancel().onComplete(handleResponse)(execContext)
  }

  def publisherSubscription(): Option[Subscription] = publisher.processorSubscription

  override def request(n: Long): Unit = {
    if (n <= 0) {
      val err = new IllegalArgumentException(s"Invalid request for $n elements. According to the reactive stream spec #309 only positive values may be requested")
      notifyError(err)
    } else {
      doRequest(n, publisher.propagateSubscriberRequestsToOurSubscription)
    }
  }

  def request(n: Long, propagateSubscriberRequest: Boolean): Unit = {
    doRequest(n, propagateSubscriberRequest)
  }

  private def doRequest(n: Long, propagateSubscriberRequest: Boolean): Unit = {
    subscriptionApi.onRequest(n).onComplete { res =>
      handleResponse(res)
      if (propagateSubscriberRequest) {
        // the child of this historic processor is pulling, so the historic processor
        // should potentially pull in turn...
        publisher.onSubscriberRequestingUpTo(state.maxRequestedIndex())
      }
    }(execContext)
  }
}