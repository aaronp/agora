package lupin.pub.flatmap

import org.reactivestreams.Subscription

/**
  * Exposing this to allow subscriptions to pattern-match (or cast) and take advantage
  * of explicitly pulling from or cancelling the outer subscription as well
  */
trait FlatmappedSubscription extends Subscription {
  def requestFromOuter(n: Long): Unit

  def cancelOuter(): Unit
}
