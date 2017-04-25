package jabroni.api.exchange


trait SubscriptionDao[T, C] {
  def save(requested: Long, subscription: FilteringSubscriber[T, C])

  def matching(data: T): Iterator[(Long, FilteringSubscriber[T, C])]

  def decrement(subscription: FilteringSubscriber[T, C])

  def increment(requested: Long, subscription: FilteringSubscriber[T, C])

  def remove(subscription: FilteringSubscriber[T, C])
}

object SubscriptionDao {
  def apply[T, C]() = new Buffer[T, C]

  class Buffer[T, C]() extends SubscriptionDao[T, C] {
    private var buffer = Map[C, List[(Long, FilteringSubscriber[T, C])]]()

    override def save(requested: Long, subscription: FilteringSubscriber[T, C]): Unit = {
      update(subscription)(_ => requested)
    }

    override def matching(data: T): Iterator[(Long, FilteringSubscriber[T, C])] = {
      val found = for {
        values <- buffer.values
        pear@(_, subscriber) <- values
        if subscriber.filter.accept(data)
      } yield pear
      found.iterator
    }

    override def decrement(subscription: FilteringSubscriber[T, C]): Unit = {
      update(subscription)(_ - 1)
    }

    override def increment(requested: Long, subscription: FilteringSubscriber[T, C]): Unit = {
      update(subscription)(_ + requested)
    }

    def update(subscription: FilteringSubscriber[T, C])(change: Long => Long): Unit = {
      val list = buffer.getOrElse(subscription.subscriberData, Nil)
      val newList = list.find(_ == subscription) match {
        case None => (change(0).ensuring(_ > 0), subscription) :: list
        case Some(pear@(originalN, _)) =>
          val removed = list diff List(pear)
          (change(originalN).ensuring(_ > 0), subscription) :: removed
      }
      buffer = buffer.updated(subscription.subscriberData, newList)
    }

    override def remove(subscription: FilteringSubscriber[T, C]): Unit = {
      val list = buffer.getOrElse(subscription.subscriberData, Nil)
      list.find(_ == subscription) foreach { pear =>
        val removed = list diff List(pear)
        buffer = buffer.updated(subscription.subscriberData, removed)
      }
    }
  }

}