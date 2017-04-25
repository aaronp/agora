package jabroni.api.exchange

import org.reactivestreams.{Subscriber, Subscription}


trait InputDao[T] {
  def save(input: T)

  def matching(n: Long, filter: T => Boolean): Iterator[T]
}

trait SubscriptionDao[T, C] {
  def save(requested: Long, subscription: FilteringSubscriber[T, C])

  def matching(data: T): Iterator[(Long, FilteringSubscriber[T, C])]

  def decrement(subscription: FilteringSubscriber[_ >: T, C])

  def increment(requested: Long, subscription: FilteringSubscriber[_ >: T, C])

  def remove(subscription: FilteringSubscriber[_ >: T, C])
}

/**
  * a subscription which can filter the inputs sent to it
  * (and possibly serialize itself)
  *
  * @tparam T
  */
trait FilteringSubscriber[T, C] extends Subscriber[T] {
  def subscriberData: C

  def filter: Filter[T]
}

trait FilteredPublisher[T, C] {
  def subscribe(subscriber: FilteringSubscriber[_ >: T, C])
}


trait FilterProvider[A, B] {
  def apply(in: A): B => Boolean

  /**
    * Given the matching data, choose those which match
    *
    * @param iter
    * @return
    */
  def select[T <% B](iter: Iterable[T]): Iterable[T]
}

object FilterProvider {

}

trait Filter[T] {
  def accept(in: T): Boolean
}


//class Exchange[F[_] : DataFilter[F], T](store: InputStore[T]) extends Publisher[F] {
class Exchange[T, C](inputDao: InputDao[T], subscriptionDao: SubscriptionDao[T, C])(implicit filterProvider: FilterProvider[T, C]) extends FilteredPublisher[T, C] {
  //  private val filter: DataFilter[F] = implicitly[DataFilter[F]]

  def offer(data: T) = {
    val consumerFilter = filterProvider(data)

    val candidateSubscriptions = subscriptionDao.matching(data)
    val matches: Iterator[(Long, FilteringSubscriber[T, C])] = candidateSubscriptions.filter {
      case (_, subscription) => consumerFilter(subscription.subscriberData)
    }

    implicit def asC(pear: (Long, FilteringSubscriber[T, C])) = pear._2.subscriberData

    val chosen = filterProvider.select(matches.toIterable).map(_._2)
    val notified = notifySubscriber(chosen, data, true)
    // no eligible subscribers ... save the data to be matched later
    if (notified == 0) {
      inputDao.save(data)
    }
  }

  private def notifySubscriber(subscribers: Traversable[FilteringSubscriber[_ >: T, C]], data: T, decrement: Boolean): Int = {
    subscribers.foldLeft(0) {
      case (count, subscription) =>
        try {
          subscription.onNext(data)
          if (decrement) {
            subscriptionDao.decrement(subscription)
          }
        } catch {
          case err: Throwable => subscription.onError(err)
        }
        count + 1
    }
  }


  private[exchange] def take(n: Long, subscriber: FilteringSubscriber[_ >: T, C]) = {
    val filter: Filter[_ >: T] = subscriber.filter
    val matchingData: Iterator[T] = inputDao.matching(n, filter.accept)

    // check the data likes us back
    val candidates: Iterator[T] = matchingData.filter { data =>
      filterProvider(data)(subscriber.subscriberData)
    }

    val sent = candidates.foldLeft(0) {
      case (count, data) =>
        notifySubscriber(List(subscriber), data, false)
        count + 1
    }

    if (sent < n) {
      subscriptionDao.increment(n - sent, subscriber)
    }
  }

  override def subscribe(subscriber: FilteringSubscriber[_ >: T, C]): Unit = {
    subscriber.onSubscribe(new ExchangeSubscription(this, subscriber))
  }

  private[exchange] def cancel(subscriber: FilteringSubscriber[_ >: T, C]) = {
    subscriptionDao.remove(subscriber)
  }

}

class ExchangeSubscription[T, C](exchange: Exchange[T, C], subscriber: FilteringSubscriber[_ >: T, C]) extends Subscription {
  override def cancel(): Unit = exchange.cancel(subscriber)

  override def request(n: Long): Unit = exchange.take(n, subscriber)
}
