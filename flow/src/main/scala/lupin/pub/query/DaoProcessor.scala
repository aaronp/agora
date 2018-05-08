package lupin.pub.query

import lupin.Publishers
import lupin.example.Accessor
import lupin.pub.FIFO
import lupin.pub.passthrough.PassthroughProcessorInstance
import lupin.pub.query.DaoProcessor.CrudOperation
import lupin.sub.BaseSubscriber
import org.reactivestreams.{Publisher, Subscriber}

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

  class Instance[K, T](override val defaultInput: Set[K] = Set.empty)(implicit accessor: Accessor.Aux[T, K], execContext: ExecutionContext)
    extends DaoProcessor[K, T]
      with BaseSubscriber[T] {

    // TODO - replace this map w/ some key/value store
    private var valuesById: Map[K, T] = Map[K, T]()

    private val outer = this
    private val publisher = new PassthroughProcessorInstance[CrudOperation[K, T]](() => FIFO[Option[CrudOperation[K, T]]]()) {
      override def request(n: Long = 1) = {
        outer.request(n)
      }
    }

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
      val creates: Set[Create[K, T]] = ids.flatMap { id =>
        valuesById.get(id).map { value =>
          val key = accessor.get(value)
          Create[K, T](key, value)
        }
      }

      if (creates.nonEmpty) {
        val initial: Publisher[CrudOperation[K, T]] = Publishers.forValues(creates)
        Publishers.concat(initial) { sub =>
          publisher.subscribe(sub, queue)
        }.subscribe(subscriber)
      } else {
        publisher.subscribe(subscriber, queue)
      }

    }

    override def onNext(value: T): Unit = {
      val key = accessor.get(value)
      val crud = valuesById.get(key) match {
        case None => Create[K, T](key, value)
        case Some(_) => Update[K, T](key, value)
      }
      valuesById = valuesById.updated(key, value)
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
