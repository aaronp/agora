package lupin.pub.sequenced

private[sequenced] class MaxRequest(initialValue: Long = -1L) {

  private object MaxRequestedIndexLock

  // the maximum index any of the subscribers are pulling for
  private var maxRequested = -1L

  def get() = MaxRequestedIndexLock.synchronized {
    maxRequested.max(0)
  }

  /**
    * We keep track of the greatest requested index amongst all our subscribers, and then 'request(n)' from
    * our own subscription based on the diff between the new (potentially) max index and the last one
    *
    * This simply updates the max index and returns the positive difference needed to request
    *
    * @param potentialNewMaxIndex the new max index if it is greater than the old max index
    * @return the difference from the old max or 0 if potentialNewMaxIndex is <= old max
    */
  def update(potentialNewMaxIndex: Long): Long = {
    val potentialNewMaxRequested = potentialNewMaxIndex + 1
    MaxRequestedIndexLock.synchronized {
      if (potentialNewMaxRequested > maxRequested) {
        val diff = (potentialNewMaxRequested - maxRequested.max(0)).max(0)
        maxRequested = potentialNewMaxRequested
        diff
      } else 0
    }
  }
}
