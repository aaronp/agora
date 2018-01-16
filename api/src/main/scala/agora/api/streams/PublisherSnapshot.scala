package agora.api.streams

import agora.api.streams.ConsumerQueue.QueueLimit

case class PublisherSnapshot[K](subscribers : Map[K, SubscriberSnapshot])
case class SubscriberSnapshot(requested : Long, buffered : Long, limit : QueueLimit)
