package lupin.sub

/**
  * A subscriber which acts like a fold
  */
abstract class AccumulatingSubscriber[T, S](initialState: S) extends BaseSubscriber[T] {

  @volatile protected var state = initialState

  protected def combine(lastState: S, next: T): S

  override def onNext(value: T) = {
    state = combine(state, value)
  }
}
