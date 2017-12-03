package agora.api.streams

abstract class AccumulatingSubscriber[S, T](initialRequest: Long, initialState: S) extends BaseSubscriber[T](initialRequest: Long) {

  @volatile protected var state = initialState

  protected def combine(lastState: S, next: T): S

  override def onNext(value: T) = {
    state = combine(state, value)
  }
}
