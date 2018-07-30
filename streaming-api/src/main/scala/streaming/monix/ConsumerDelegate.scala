package streaming.monix
import monix.eval.Callback
import monix.execution.cancelables.AssignableCancelable
import monix.execution.{Ack, Cancelable, Scheduler}
import monix.reactive.Consumer
import monix.reactive.observers.Subscriber
import streaming.monix.ConsumerDelegate.DelegateSubscriber

import scala.concurrent.Future

class ConsumerDelegate[In, R](initial: Consumer[In, R]) extends Consumer[In, R] {

  private var delegate: Consumer[In, R] = initial

  def update(consumer: Consumer[In, R]): Unit = {
    delegate = consumer
  }

  override def createSubscriber(cb: Callback[R], s: Scheduler): (Subscriber[In], AssignableCancelable) = {
    val (sub: Subscriber[In], can) = delegate.createSubscriber(cb, s)
    val ds                         = new DelegateSubscriber[In, R](sub, can)
    (ds, ds)
  }
}

object ConsumerDelegate {

  class DelegateSubscriber[In, R](initialSubscriber: Subscriber[In], initialCancelable: AssignableCancelable) extends Subscriber[In] with AssignableCancelable {
    private var delegate                       = initialSubscriber
    private var cancelableDelegate             = initialCancelable
    override implicit def scheduler: Scheduler = delegate.scheduler

    override def :=(value: Cancelable) = {
      cancelableDelegate := value
      this
    }
    override def cancel(): Unit = {
      cancelableDelegate.cancel()
    }
    override def onNext(elem: In): Future[Ack] = {
      delegate.onNext(elem)
    }
    override def onError(ex: Throwable): Unit = {
      delegate.onError(ex)
    }
    override def onComplete(): Unit = {
      delegate.onComplete()
    }
  }
}
