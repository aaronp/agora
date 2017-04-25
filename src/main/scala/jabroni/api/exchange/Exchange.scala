package jabroni.api.exchange


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

  private def notifySubscriber(subscribers: Traversable[FilteringSubscriber[T, C]], data: T, decrement: Boolean): Int = {
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


  private[exchange] def take(n: Int, subscriber: FilteringSubscriber[T, C]) = {
    val filter: Filter[T] = subscriber.filter

    val matchingData: Iterator[T] = inputDao.matching(n, filter.accept)

    // check the data likes us back
    val data: Iterator[T] = matchingData.filter { data =>
      filterProvider(data)(subscriber.subscriberData)
    }

    val sent = data.foldLeft(0) {
      case (count, data) =>
        notifySubscriber(List(subscriber), data, false)
        inputDao.remove(data)
        count + 1
    }

    if (sent < n) {
      subscriptionDao.increment(n - sent, subscriber)
    }
  }

  override def subscribe(subscriber: FilteringSubscriber[T, C]): Unit = {
    subscriber.onSubscribe(new ExchangeSubscription(this, subscriber))
  }

  private[exchange] def cancel(subscriber: FilteringSubscriber[T, C]) = {
    subscriptionDao.remove(subscriber)
  }

}

