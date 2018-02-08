package agora.flow

/**
  * @tparam K the key used to track each of the publisher's subscribers
  */
trait PublisherSnapshotSupport[K] {
  def snapshot(): PublisherSnapshot[K]
}
