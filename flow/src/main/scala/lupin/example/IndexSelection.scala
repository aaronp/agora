package lupin.example

sealed trait IndexSelection {

  /** @return the first Index in this selection
    */
  def firstIndex: Option[Long]

  def contains(index: Long): Boolean
}

object IndexSelection {

  def apply(fromIndex: Long, toIndex: Long) = new IndexRange(fromIndex, toIndex)

  def apply(indices: List[Long]) = new SpecificIndices(indices.sorted)
}

case class IndexRange(val fromIndex: Long, toIndex: Long) extends IndexSelection {
  require(toIndex >= fromIndex)

  override def contains(index: Long): Boolean = index >= fromIndex && index <= toIndex

  override def firstIndex: Option[Long] = Option(fromIndex)
}

case class SpecificIndices(indices: List[Long]) extends IndexSelection {
  override def firstIndex: Option[Long] =
    if (indices.isEmpty) None
    else {
      Option(indices.min)
    }

  override def contains(index: Long): Boolean = indices.contains(index)
}
