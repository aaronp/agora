package agora.rest.stream

import lupin.PublisherSnapshot

case class DataConsumerSnapshot(name: String, republishSnapshot: PublisherSnapshot[Int])
