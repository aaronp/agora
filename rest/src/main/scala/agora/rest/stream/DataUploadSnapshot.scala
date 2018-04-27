package agora.rest.stream

import lupin.PublisherSnapshot

case class DataUploadSnapshot(name: String, dataConsumingSnapshot: PublisherSnapshot[Int], controlMessagesSnapshot: PublisherSnapshot[Int])
