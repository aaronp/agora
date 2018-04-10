package agora.flow.impl

import agora.flow.DurableProcessorReader
import org.reactivestreams.Subscriber

import scala.util.control.NonFatal
import scala.util.{Failure, Success}

/**
  * This contains the business logic which is executed within a separate runnable for reading from the
  * publisher and driving the [[org.reactivestreams.Subscriber]]
  *
  * This class is NOT thread-safe, but expects to be executed within a single thread.
  *
  * This state could be embedded in an actor which accepts [[agora.flow.impl.SubscriberState.Command]]
  * messages, or a simple Runnable which pulls commands from a queue
  */
class SubscriberState[T](subscription: Subscriber[T], dao: DurableProcessorReader[T]) {

  import SubscriberState._

  private var maxIndexAvailable: Long = -1L
  private var maxIndexRequested: Long = -1L
  private var lastIndexPushed: Long = -1L
  private var complete = false

  /** Update the execution based on the given state
    *
    * @param cmd the command to process
    * @return the result
    */
  def update(cmd: Command): CommandResult = {
    try {
      onCommandUnsafe(cmd)
    } catch {
      case NonFatal(err) => StopResult(Option(err))
    }
  }

  private def onCommandUnsafe(cmd: Command): CommandResult = {
    cmd match {
      case OnRequest(n) =>
        require(n > 0)
        maxIndexRequested = maxIndexRequested + n
        pushValues()
      case OnNewIndexAvailable(index) =>
        require(index >= maxIndexAvailable, s"Max index should never be decremented $maxIndexAvailable to $index")
        require(!complete, "OnNewIndexAvailable after complete")
        maxIndexAvailable = index
        pushValues()
      case OnCancel => StopResult(None)
      case OnComplete(index) =>
        complete = true
        require(index >= maxIndexAvailable, s"Max index should never be decremented $maxIndexAvailable to $index")
        maxIndexAvailable = index
        pushValues()
    }
  }

  private def pushValues[T](): CommandResult = {
    if (maxIndexRequested <= maxIndexAvailable) {
      while (lastIndexPushed < maxIndexRequested) {
        lastIndexPushed = lastIndexPushed + 1

        // TODO - request ranges
        dao.at(lastIndexPushed) match {
          case Success(value) =>
            subscription.onNext(value)
          case Failure(err) =>
            subscription.onError(err)
            return StopResult(Option(err))
        }
      }
      tryComplete()
    } else {
      tryComplete()
    }
  }

  private def tryComplete(): CommandResult = {
    if (complete) {
      if (lastIndexPushed == maxIndexAvailable) {
        subscription.onComplete()
        StopResult(None)
      } else {
        ContinueResult
      }
    } else {
      ContinueResult
    }
  }

}

object SubscriberState {

  /** The state input result */
  sealed trait CommandResult

  /** the carry-on-as-normal case */
  case object ContinueResult extends CommandResult

  /** the stop case, either by completion or exception/error */
  case class StopResult(error: Option[Throwable]) extends CommandResult

  /**
    * A state input
    */
  sealed trait Command

  case class OnRequest(n: Long) extends Command

  case class OnNewIndexAvailable(maxIndex: Long) extends Command

  case object OnCancel extends Command

  case class OnComplete(maxIndex: Long) extends Command

}
