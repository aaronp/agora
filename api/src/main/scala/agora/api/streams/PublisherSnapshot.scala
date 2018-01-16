package agora.api.streams

import agora.api.streams.ConsumerQueue.QueueLimit

case class PublisherSnapshot[K](subscribers : Map[K, SubscriberSnapshot[K]])
case class SubscriberSnapshot[K](requested : Long, buffered : Long, limit : QueueLimit)
