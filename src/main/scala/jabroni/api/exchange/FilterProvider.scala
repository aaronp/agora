package jabroni.api.exchange


trait FilterProvider[A, B] {
  def apply(in: A): B => Boolean

  /**
    * Given the matching data, choose those which match
    *
    * @param iter
    * @return
    */
  def select[T <% B](iter: Iterable[T]): Iterable[T]
}