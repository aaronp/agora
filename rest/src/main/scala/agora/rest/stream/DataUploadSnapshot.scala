package agora.rest.stream

import agora.flow.PublisherSnapshot

case class DataUploadSnapshot(name: String, dataConsumingSnapshot: PublisherSnapshot[Int], controlMessagesSnapshot: PublisherSnapshot[Int])
