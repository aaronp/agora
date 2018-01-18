package agora.rest.stream

import agora.api.streams.PublisherSnapshot

case class DataConsumerSnapshot(
                                 name : String, republishSnapshot: PublisherSnapshot[Int]
                               )
