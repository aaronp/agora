package agora.rest.stream

import agora.flow.PublisherSnapshot

case class DataConsumerSnapshot(name: String, republishSnapshot: PublisherSnapshot[Int])
