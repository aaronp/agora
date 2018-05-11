package lupin.pub.query

sealed trait CrudOperation[K] {
  def key: K
}

case class Create[K, T](override val key: K) extends CrudOperation[K]

case class Update[K, T](override val key: K) extends CrudOperation[K]

case class Delete[K, T](override val key: K) extends CrudOperation[K]
