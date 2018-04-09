package agora.flow

import agora.flow.ConsumerQueue.QueueLimit

case class PublisherSnapshot[K](subscribers: Map[K, SubscriberSnapshot]) {
  override def toString: String = {
    subscribers
      .map {
        case (k, snap) => s"\t${k.toString.padTo(8, ' ')}: $snap"
      }
      .mkString("\n")
  }
}
case class SubscriberSnapshot(name: String,
                              totalRequested: Long,
                              totalPushed: Long,
                              totalReceived: Long,
                              currentlyRequested: Long,
                              buffered: Long,
                              limit: QueueLimit) {
  override def toString: String =
    s"$name {$currentlyRequested currently requested ($totalRequested total requested), $totalPushed pushed, $totalReceived received, $buffered buffered, $limit}"
}
