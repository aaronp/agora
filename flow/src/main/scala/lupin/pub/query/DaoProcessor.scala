package lupin.pub.query

import lupin.Publishers
import lupin.data.Accessor
import lupin.pub.FIFO
import lupin.pub.passthrough.PassthroughProcessorInstance
import lupin.sub.BaseSubscriber
import org.reactivestreams.{Publisher, Subscriber}

import scala.concurrent.ExecutionContext

/**
  * Represents a special case of a QueryPublisher which will publish [[CrudOperation]]s for a set of IDs of type K.
  *
  * @tparam K the key type
  * @tparam T the entity type
  */
trait DaoProcessor[K, T] extends QueryPublisher[Set[K], CrudOperation[K, T]]

object DaoProcessor {

  def apply[K, T](inputDao: SyncDao[K, T] = null, filter: Set[K] = Set[K]())(implicit accessor: Accessor.Aux[T, K], execContext: ExecutionContext) = {
    val dao = if (inputDao == null) {
      SyncDao[K, T](accessor)
    } else {
      inputDao
    }
    new Instance[K, T](dao, filter)
  }

  class Instance[K, T](inputDao: SyncDao[K, T], override val defaultInput: Set[K])(implicit accessor: Accessor.Aux[T, K], execContext: ExecutionContext)
      extends DaoProcessor[K, T]
      with BaseSubscriber[T] {

    private var dao = inputDao

    private val outer = this
    private val publisher = new PassthroughProcessorInstance[CrudOperation[K, T]](() => FIFO[Option[CrudOperation[K, T]]]()) {
      override def request(n: Long = 1) = {
        outer.request(n)
      }
    }

    /**
      *
      * @param ids
      * @param subscriber
      */
    override def subscribeWith(ids: Set[K], subscriber: Subscriber[_ >: CrudOperation[K, T]]): Unit = {

      val queue: FIFO[Option[CrudOperation[K, T]]] = {
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
        dao.get(id).map { value =>
          val key = accessor.get(value)
          Create[K, T](key, value)
        }
      }

      /**
        * If some ids are specified, then we need to publish the existing data as Create operations
        * for those before publishing updates
        */
      if (creates.nonEmpty) {
        val initial: Publisher[CrudOperation[K, T]] = Publishers.forValues(creates)
        Publishers
          .concat(initial) { sub =>
            publisher.subscribe(sub, queue)
          }
          .subscribe(subscriber)
      } else {
        publisher.subscribe(subscriber, queue)
      }

    }

    override def onNext(value: T): Unit = {
      val (crudOp, newDao) = dao.update(value)
      dao = newDao
      publisher.onNext(crudOp)
    }

    override def onError(t: Throwable): Unit = {
      publisher.onError(t)
    }

    override def onComplete(): Unit = {
      publisher.onComplete()
    }
  }

}
