package lupin.pub.query

import lupin.Publishers
import lupin.data.Accessor
import lupin.pub.FIFO
import lupin.pub.passthrough.PassthroughProcessorInstance
import lupin.sub.BaseSubscriber
import org.reactivestreams.{Publisher, Subscriber, Subscription}

import scala.collection.mutable.ListBuffer
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

      /**
        * If some ids are specified, then we need to publish the existing data as Create operations
        * for those before publishing updates
        */
      if (ids.nonEmpty) {

        class InternalBuffer[B] extends Publisher[B] with BaseSubscriber[B] {
          private val listBuffer = ListBuffer[B]()

          lazy val outerSubscription = subscription()
          var connected: Subscriber[_ >: B] = null

          private var errorOpt = Option.empty[Throwable]
          private var completed = false

          override def onError(t: Throwable): Unit = {
            if (connected != null) {
              connected.onError(t)
            } else {
              errorOpt = Option(t)
            }
          }

          override def onComplete(): Unit = {
            if (connected != null) {
              connected.onComplete()
            } else {
              completed = true
            }
          }

          override def subscribe(s: Subscriber[_ >: B]): Unit = {
            // we're now joining onto the outer publisher
            require(connected == null)
            connected = s
            connected.onSubscribe(new Subscription {
              var totalRequested = 0L

              override def request(n: Long): Unit = {
                if (listBuffer.nonEmpty) {
                  totalRequested = totalRequested + n
                  if (totalRequested < 0) {
                    totalRequested = Long.MaxValue
                  }
                  while (listBuffer.nonEmpty && totalRequested > 0) {
                    connected.onNext(listBuffer.remove(0))
                    totalRequested = totalRequested - 1
                  }
                  if (listBuffer.isEmpty && totalRequested > 0) {
                    outerSubscription.request(totalRequested)
                  }
                } else {
                  outerSubscription.request(n)
                }
              }

              override def cancel(): Unit = {
                outerSubscription.cancel()
              }
            })

            errorOpt.foreach(connected.onError)
            if (completed && errorOpt.isEmpty) {
              connected.onComplete()
            }
          }

          override def onNext(value: B): Unit = {
            // either buffering or direct
            if (connected == null || listBuffer.nonEmpty) {
              listBuffer += value
            } else {
              connected.onNext(value)
            }
          }
        }

        val buffer = new InternalBuffer[CrudOperation[K, T]]
        publisher.subscribe(buffer, queue)
        buffer.request(1)

        // now read the data. The gap between having subscribed (above) and now reading the data
        // *should* eliminate the race-condition of missing updates: If we first read the data,
        // then subscribed to udpates, we would miss any updates which occur in between those two actions
        val requestedData: Set[Create[K, T]] = ids.flatMap { id =>
          dao.get(id).map { value =>
            val key = accessor.get(value)
            Create[K, T](key, value)
          }
        }

        val initial: Publisher[CrudOperation[K, T]] = Publishers.forValues(requestedData)
        val joined = Publishers.concat(initial) { sub =>
          //          buffer.dec()
          buffer.subscribe(sub)
        }

        joined.subscribe(subscriber)
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
