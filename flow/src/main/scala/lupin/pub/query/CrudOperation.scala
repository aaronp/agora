package lupin.pub.query


sealed trait CrudOperation[K, T] {
  def key: K
}

case class Create[K, T](override val key: K, value: T) extends CrudOperation[K, T]

case class Update[K, T](override val key: K, value: T) extends CrudOperation[K, T]

case class Delete[K, T](override val key: K) extends CrudOperation[K, T]
