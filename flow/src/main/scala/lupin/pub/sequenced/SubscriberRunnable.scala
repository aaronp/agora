package lupin.pub.sequenced

import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.Promise
import scala.util.control.NonFatal

/** A runnable wrapper to drive the SubscriberState */
private class SubscriberRunnable[T](state: SubscriberState[T], queue: Queue) extends Runnable with StrictLogging {

  private def pullLoop() = {
    val (firstCmd, firstPromise)             = next()
    var result: SubscriberStateCommandResult = state.update(firstCmd)
    firstPromise.trySuccess(result)

    while (result == ContinueResult) {
      val (cmd, promise) = next()
      result = state.update(cmd)
      promise.trySuccess(result)
    }
    result
  }

  private def next(): (SubscriberStateCommand, Promise[SubscriberStateCommandResult]) = {
    logger.trace("blocking on queue.take()")
    val value = queue.take()
    logger.trace(s"queue.take() returning $value")
    value
  }

  override def run(): Unit = {
    logger.debug("Waiting for first command...")
    val result = try {
      pullLoop()
    } catch {
      case NonFatal(err) =>
        logger.error(s"Pull loop threw $err", err)
    }
    logger.debug(s"SubscriberRunnable completing with ${result}")
  }
}
