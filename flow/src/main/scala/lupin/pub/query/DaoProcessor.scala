package lupin.pub.query

import lupin.example.Accessor
import lupin.pub.FIFO
import lupin.pub.passthrough.PassthroughPublisher
import lupin.pub.query.DaoProcessor.CrudOperation
import lupin.sub.BaseSubscriber
import org.reactivestreams.Subscriber

import scala.concurrent.ExecutionContext

/**
  * Represents a publisher which can request updates for an entity of a particular ID (or None)
  *
  * @tparam K
  * @tparam T
  */
trait DaoProcessor[K, T] extends QueryPublisher[Set[K], CrudOperation[K, T]]

object DaoProcessor {

  sealed trait CrudOperation[K, T] {
    def key: K
  }

  case class Create[K, T](override val key: K, value: T) extends CrudOperation[K, T]

  case class Update[K, T](override val key: K, value: T) extends CrudOperation[K, T]

  case class Delete[K, T](override val key: K) extends CrudOperation[K, T]

  def apply[K, T](filter: Set[K] = Set.empty)(implicit accessor: Accessor.Aux[T, K], execContext: ExecutionContext) = {
    new Instance[K, T](filter)
  }

  class Instance[K, T](override val defaultInput: Set[K] = Set.empty)(implicit accessor: Accessor.Aux[T, K], execContext: ExecutionContext) extends DaoProcessor[K, T] with BaseSubscriber[T] {

    // TODO - replace this map w/ some key/value store
    private var valuesById: Map[K, T] = Map[K, T]()

    private val publisher = PassthroughPublisher[CrudOperation[K, T]]()

    override def subscribeWith(ids: Set[K], subscriber: Subscriber[_ >: CrudOperation[K, T]]): Unit = {

      val queue = {
        val simple = FIFO[Option[CrudOperation[K, T]]]()
        if (ids.nonEmpty) {
          FIFO.filter(simple) { op =>
            op.map(_.key).exists(ids.contains)
          }
        } else {
          simple
        }
      }
      publisher.subscribe(subscriber, queue)
    }

    override def onNext(value: T): Unit = {
      val key = accessor.get(value)
      valuesById = valuesById.updated(key, value)
      val crud = valuesById.get(key) match {
        case None => Create[K, T](key, value)
        case Some(_) => Update[K, T](key, value)
      }
      publisher.onNext(crud)
    }

    override def onError(t: Throwable): Unit = {
      publisher.onError(t)
    }

    override def onComplete(): Unit = {
      publisher.onComplete()
    }
  }

}
