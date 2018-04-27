package lupin.pub.sequenced

import java.util

import com.typesafe.scalalogging.LazyLogging

import scala.compat.Platform
import scala.concurrent.Promise

/**
  * Represents a message sent from a subscriber to its publisher
  */
sealed trait SubscriberStateCommand

case class OnRequest(n: Long) extends SubscriberStateCommand

case class OnNewIndexAvailable(maxIndex: Long) extends SubscriberStateCommand

case object OnCancel extends SubscriberStateCommand

case class OnComplete(maxIndex: Long) extends SubscriberStateCommand

case class OnError(error: Throwable) extends SubscriberStateCommand

object SubscriberStateCommand extends LazyLogging {

  def conflate(queue: Queue): Queue = {
    val buffer = new CollapseBuffer

    val jList = new util.ArrayList[(SubscriberStateCommand, Promise[SubscriberStateCommandResult])]()
    queue.drainTo(jList)

    val iter  = jList.iterator()
    var count = 0
    while (iter.hasNext) {
      val (cmd, promise) = iter.next()
      buffer.update(cmd, promise)
      count += 1
    }
    val newCommands = buffer.elements()
    queue.addAll(newCommands)

    if (count == newCommands.size) {
      val sample = newCommands.toArray.take(20).mkString(Platform.EOL)
      logger.warn(s"Conflated $count commands to ${newCommands.size} had no effect: $sample")
    } else {
      logger.debug(s"Conflated $count commands to ${newCommands.size}")
    }

    queue
  }

  /**
    * Logic to conflate a Queue of SubscriberStateCommand commands
    *
    * If we remove (conflate) a command, we need to try and complete its promise. Otherwise
    * we should leave the promises as-is.
    *
    * By completing the promise with 'ContinueResult', that instructs the state machine to keep
    * on processing updates.
    */
  private[sequenced] class CollapseBuffer {

    // these are publisher commands
    private var onNewIndexAvailable = Option.empty[(Long, Promise[SubscriberStateCommandResult])]

    // we need to keep the promise so that its only completed after the others before it
    private var onComplete = Option.empty[(Long, Promise[SubscriberStateCommandResult])]
    private var onErrors   = List[(OnError, Promise[SubscriberStateCommandResult])]()

    // these are subscriber messages
    private var onRequest      = Option.empty[(Long, Promise[SubscriberStateCommandResult])]
    private var cancelRequests = List[Promise[SubscriberStateCommandResult]]()

    def isCancelled = cancelRequests.nonEmpty

    def isInError = onErrors.nonEmpty

    def firstError = onErrors.headOption.map(_._1.error)

    /**
      * if we have an error, complete or cancel, those will be at the end
      * as any 'new index available' or 'onRequest' which exists must
      * have been received before any of those, since we stop conflating them
      * once we know we're going to quit
      *
      * as far as 'on new index' or 'onRequest', ordering shouldn't matter,
      * but we bias the new index one 'cause we'll just assume there's more of
      * a change that elements were requested (a fast consumer) than data ready
      * (a fast producer)
      *
      * @return the elements used to repopulate the queue
      */
    def elements(): java.util.Collection[(SubscriberStateCommand, Promise[SubscriberStateCommandResult])] = {
      val list = new util.ArrayList[(SubscriberStateCommand, Promise[SubscriberStateCommandResult])](6)

      onNewIndexAvailable.foreach {
        case (maxIndex, promise) => list.add(OnNewIndexAvailable(maxIndex) -> promise)
      }
      onRequest.foreach {
        case (n, promise) => list.add(OnRequest(n) -> promise)
      }
      // if we have a complete, it's only 'cause we saw it before any errors,
      // so add that first
      onComplete.foreach {
        case (n, promise) => list.add(OnComplete(n) -> promise)
      }

      onErrors.foreach(list.add)

      cancelRequests.map(OnCancel -> _).foreach(list.add)

      list
    }

    /**
      * update the conflated messages w/ the next command and promise
      *
      * @param newCommand
      * @param newPromise
      * @return
      */
    def update(newCommand: SubscriberStateCommand, newPromise: Promise[SubscriberStateCommandResult]) = {
      newCommand match {
        case req: OnRequest           => conflateOnRequest(req, newPromise)
        case req: OnNewIndexAvailable => conflateOnNewIndexAvailable(req, newPromise)
        case OnCancel                 =>
          // if we received a 'complete' before the cancel, then we should honor that and should be able to
          // ignore the cancel
          if (onComplete.isEmpty && !isInError) {
            if (cancelRequests.isEmpty) {
              cancelRequests = newPromise :: cancelRequests
            } else {
              newPromise.trySuccess(ContinueResult)
            }
          } else {
            // we're swallowing the cancel request, so ensure we complete the promise
            // and allow the state to continue to update
            newPromise.trySuccess(ContinueResult)
          }
        case OnComplete(maxIndex) =>
          onComplete = onComplete match {
            case None =>
              if (isInError) {
                // we've seen an error already, so ignore this OnComplete notification
                newPromise.trySuccess(StopResult(firstError))
                None
              } else {
                // just remember we saw an OnComplete
                Some(maxIndex -> newPromise)
              }
            case Some((_, oldPromise)) =>
              // we're now in some kind of error state 'cause we seem to have been given 'OnComplete' at least
              // twice from a misbehaving publisher
              val newErrors = {
                val err = new IllegalStateException("Encountered multiple 'onComplete' messages while conflating the queue")
                (OnError(err), Promise[SubscriberStateCommandResult]())
              }

              onErrors = newErrors :: onErrors
              newPromise.trySuccess(StopResult(firstError))
              oldPromise.trySuccess(StopResult(firstError))
              None
          }
        case err: OnError =>
          // just don't worry about conflating the errors
          // a nicely behaved publisher will remove the subscription on error
          onErrors = (err, newPromise) :: onErrors
      }
    }

    def conflateOnNewIndexAvailable(req: OnNewIndexAvailable, newPromise: Promise[SubscriberStateCommandResult]) = {
      onNewIndexAvailable = conflateLongValues(onNewIndexAvailable, newPromise) {
        case -1L         => req.maxIndex
        case oldMaxIndex => oldMaxIndex.max(req.maxIndex)
      }
    }

    /**
      * try to either collapse this if there's another with which it can be combined, or remove it if we're cancelled
      */
    def conflateOnRequest(req: OnRequest, newPromise: Promise[SubscriberStateCommandResult]) = {
      onRequest = conflateLongValues(onRequest, newPromise) {
        case -1L => req.n
        case oldN =>
          val possiblyOverflowed = oldN + req.n
          if (possiblyOverflowed <= 0) {
            Long.MaxValue
          } else {
            possiblyOverflowed
          }
      }
    }

    private def conflateLongValues(opt: Option[(Long, Promise[SubscriberStateCommandResult])], newPromise: Promise[SubscriberStateCommandResult])(
        updateLong: Long => Long) = {
      opt match {
        case Some((oldValue, promise)) =>
          if (isCancelled || isInError) {
            promise.trySuccess(ContinueResult)
            None
          } else {
            promise.trySuccess(ContinueResult)
            val newMax = updateLong(oldValue)
            Some(newMax -> newPromise)
          }
        case None =>
          if (isCancelled || isInError) {
            newPromise.trySuccess(ContinueResult)
            None
          } else {
            Some(updateLong(-1L) -> newPromise)
          }
      }
    }
  }

}
