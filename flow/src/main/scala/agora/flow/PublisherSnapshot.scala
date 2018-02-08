package agora.flow

import agora.flow.ConsumerQueue.QueueLimit

case class PublisherSnapshot[K](subscribers: Map[K, SubscriberSnapshot])
case class SubscriberSnapshot(name : String, totalRequested: Long, totalPushed: Long, totalReceived: Long, currentlyRequested: Long, buffered: Long, limit: QueueLimit)
