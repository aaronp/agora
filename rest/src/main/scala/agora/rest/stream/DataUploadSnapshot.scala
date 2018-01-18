package agora.rest.stream

import agora.api.streams.PublisherSnapshot

case class DataUploadSnapshot(name : String, dataConsumingSnapshot: PublisherSnapshot[Int], controlMessagesSnapshot: PublisherSnapshot[Int])
