package crud.monix

import crud.api.{CrudDsl, CrudRequest}
import monix.execution.{Ack, Cancelable, Scheduler}
import monix.reactive.observers.Subscriber
import monix.reactive.{Observer, _}

/**
  * A CrudDsl which will simply publish the events run through it
  *
  * The syn and observer are two components of a pipe
  *
  * @param sync the observer to notify of events
  * @param obs  a wrapped observable connected to the syn
  */
class CrudDslMonix[ID, T](val sync: Observer.Sync[CrudRequest[_]],
                   val obs: Observable[CrudRequest[_]])
  extends Observable[CrudRequest[_]]
    with CrudDsl[CrudRequest, ID, T]
    with Observer.Sync[CrudRequest[_]]
    with AutoCloseable {
  override def run[A](req: CrudRequest[A]): CrudRequest[A] = {
    onNext(req)
    req
  }

  override def unsafeSubscribeFn(subscriber: Subscriber[CrudRequest[_]]): Cancelable = {
    obs.unsafeSubscribeFn(subscriber)
  }

  override def close(): Unit = onComplete()

  override def onNext(elem: CrudRequest[_]): Ack = sync.onNext(elem)

  override def onError(ex: Throwable): Unit = sync.onError(ex)

  override def onComplete(): Unit = sync.onComplete()
}

object CrudDslMonix {

  def apply[ID, T]()(implicit scheduler: Scheduler): CrudDslMonix[ID, T] = {
    val (observer, observable) = Pipe.replay[CrudRequest[_]].concurrent
    new CrudDslMonix(observer, observable)
  }
}