package jabroni.exchange

import jabroni.api.client.SubmitJob
import org.reactivestreams.{Publisher, Subscriber}

class WorkPublisher extends Publisher[SubmitJob] {
  override def subscribe(subscriber: Subscriber[_ >: SubmitJob]): Unit = {
    ???
  }
}
