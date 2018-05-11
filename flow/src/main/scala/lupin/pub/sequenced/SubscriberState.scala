package lupin.pub.sequenced

import com.typesafe.scalalogging.StrictLogging
import org.reactivestreams.Subscriber

import scala.util.control.NonFatal
import scala.util.{Failure, Success, Try}

/**
  * This contains the business logic which is executed within a separate runnable for reading from the
  * publisher and driving the [[org.reactivestreams.Subscriber]]
  *
  * This class is NOT thread-safe, but expects to be executed within a single thread.
  *
  * This state could be embedded in an actor which accepts [[SubscriberStateCommand]]
  * messages, or a simple Runnable which pulls commands from a queue
  */
class SubscriberState[T](subscription: Subscriber[_ >: (Long, T)], dao: DurableProcessorReader[T], initialRequestedIndex: Long) extends StrictLogging {

  private var maxIndexAvailable: Long           = -1L
  @volatile private var maxIndexRequested: Long = initialRequestedIndex
  private var lastIndexPushed: Long             = initialRequestedIndex
  private var complete                          = false

  private[sequenced] def maxRequestedIndex() = maxIndexRequested

  /** Update the execution based on the given state
    *
    * @param cmd the command to process
    * @return the result
    */
  def update(cmd: SubscriberStateCommand): SubscriberStateCommandResult = {
    try {
      logger.trace(s"handling $cmd")
      val res = onCommandUnsafe(cmd)
      logger.trace(s"$cmd returned $res")
      res
    } catch {
      case NonFatal(err) =>
        logger.error(s"$cmd threw $err")
        Try(subscription.onError(err))
        StopResult(Option(err))
    }
  }

  private def onCommandUnsafe(cmd: SubscriberStateCommand): SubscriberStateCommandResult = {
    cmd match {
      case OnRequest(n) =>
        require(n > 0)
        maxIndexRequested = maxIndexRequested + n
        // As governed by rule 3.17, when demand overflows `Long.MAX_VALUE` we treat the signalled demand as "effectively unbounded"
        if (maxIndexRequested < 0L) {
          maxIndexRequested = Long.MaxValue
        }

        pushValues()
      case OnNewIndexAvailable(index) =>
        require(index >= maxIndexAvailable, s"Max index should never be decremented $maxIndexAvailable to $index")
        require(!complete, "OnNewIndexAvailable after complete")
        maxIndexAvailable = index
        pushValues()
      case OnCancel => CancelResult
      case OnError(err) =>
        subscription.onError(err)
        StopResult(Option(err))
      case OnComplete(index) =>
        complete = true
        require(index >= maxIndexAvailable, s"Max index should never be decremented $maxIndexAvailable to $index")
        maxIndexAvailable = index
        pushValues()
    }
  }

  private def pushValues[T](): SubscriberStateCommandResult = {
    val max = maxIndexAvailable.min(maxIndexRequested)
    logger.trace(s"pushValues(req=$maxIndexRequested, available=$maxIndexAvailable, lastPushed=$lastIndexPushed, max=$max)")

    while (lastIndexPushed < max) {
      lastIndexPushed = lastIndexPushed + 1

      logger.trace(s"reading $lastIndexPushed")
      // TODO - request ranges
      dao.at(lastIndexPushed) match {
        case Success(value) =>
          subscription.onNext(lastIndexPushed -> value)
        case Failure(err) =>
          logger.error(s"subscriber was naughty and threw an exception on onNext for $lastIndexPushed: $err")
          subscription.onError(err)
          return StopResult(Option(err))
      }
    }
    tryComplete()

  }

  private def tryComplete(): SubscriberStateCommandResult = {
    val res = if (complete) {
      if (lastIndexPushed >= maxIndexAvailable) {
        subscription.onComplete()
        StopResult(None)
      } else {
        ContinueResult
      }
    } else {
      ContinueResult
    }
    logger.trace(s"tryComplete(complete=$complete, lastIndexPushed=$lastIndexPushed, maxIndexAvailable=$maxIndexAvailable) returning $res")

    res
  }

}
