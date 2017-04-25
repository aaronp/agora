package jabroni.api.exchange


/**
  * Represents a higher-order function which, when given an 'A', can produce a filter for 'B'
  *
  * It also knows how to select a filtered collection
  *
  * @tparam A
  * @tparam B
  */
trait FilterProvider[A, B] {
  def apply(in: A): B => Boolean

  /**
    * Given the matching data, choose those which match
    *
    * @param iter
    * @return
    */
  def select[T <% B](iter: Iterable[T]): Iterable[T] = iter
}

object FilterProvider {

  class Provider[A, B](fromA: A => B => Boolean, mode : SelectionMode) extends FilterProvider[A, B] {
    override def apply(in: A): (B) => Boolean = fromA(in)
    override def select[T <% B](iter: Iterable[T]): Iterable[T] = {
      ???
    }
  }

  def apply[A, B](f: A => B => Boolean)(implicit mode : SelectionMode): FilterProvider[A, B] = new Provider[A, B](f, mode)
}