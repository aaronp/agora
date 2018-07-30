package monix.reactive.observers.buffers
import monix.reactive.observers.Subscriber

class SingleItemBackPressuredSubscriber[A](out : Subscriber[A]) extends AbstractBackPressuredBufferedSubscriber[A, A](out, 1) {

  @volatile protected var p50, p51, p52, p53, p54, p55, p56, p57 = 5
  @volatile protected var q50, q51, q52, q53, q54, q55, q56, q57 = 5

  override protected def fetchNext(): A = {
    queue.poll()
  }

  override protected def fetchSize(r: A): Int =
    if (r == null) 0 else 1

}
