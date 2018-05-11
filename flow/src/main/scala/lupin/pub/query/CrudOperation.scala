package lupin.pub.query

sealed trait CrudOperation[K] {
  def key: K
}

object CrudOperation {
  def create[K](key: K): CrudOperation[K] = Create[K](key)

  def update[K](key: K): CrudOperation[K] = Update[K](key)

  def delete[K](key: K): CrudOperation[K] = Delete[K](key)
}

case class Create[K](override val key: K) extends CrudOperation[K]

case class Update[K](override val key: K) extends CrudOperation[K]

case class Delete[K](override val key: K) extends CrudOperation[K]
