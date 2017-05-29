package jabroni.rest.test

import miniraft.state.RaftTimer

import scala.concurrent.duration.FiniteDuration

class TestTimer extends RaftTimer {
  var cancelCalls = 0
  var resetCalls = List[Option[FiniteDuration]]()
  var cancelResponse = true
  var resetResponse = true

  /**
    * Cancel's the current timeout
    *
    * @return true if the call had effect
    */
  override def cancel(): Boolean = {
    cancelCalls = cancelCalls + 1
    cancelResponse
  }

  /** resets the timeout
    *
    * The implementation should provide sensible default timeouts,
    * but the caller can opt to provide a delay. For example,
    * in the occasion of an endpoint failure or a user interaction,
    * we may want to schedule at a sooner (or event immediate) time
    *
    * In the event of a timeout, the call-back can elect to
    * schedule another timeout event at the given delay.
    *
    * @param delay the delay to use when set, otherwise None
    */
  override def reset(delay: Option[FiniteDuration]): Boolean = {
    resetCalls = delay :: resetCalls
    resetResponse
  }
}
