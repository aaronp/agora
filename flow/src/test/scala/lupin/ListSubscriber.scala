package lupin

import lupin.sub.BaseSubscriber

class ListSubscriber[T]() extends BaseSubscriber[T] {

  private var elms = List[T]()

  def received() = elms

  def receivedInOrderReceived() = received.reverse

  def clear() = {
    elms = Nil
  }

  override def onNext(t: T): Unit = {
    elms = t :: elms
  }
}
