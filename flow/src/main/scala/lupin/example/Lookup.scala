package lupin.example

trait Lookup[K, T] {
  def get(id: K): Option[T]
}
