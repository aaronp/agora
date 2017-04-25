package jabroni.api.exchange

trait Filter[T] {
  def accept(in: T): Boolean
}
