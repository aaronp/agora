package lupin.pub.join

import com.typesafe.scalalogging.StrictLogging
import lupin.pub.FIFO

/**
  * A FIFO queue which will combine the left and right queues. This class is thread-safe.
  *
  * Either an Option[LeftUpdate] or Option[RightUpdate] will be enqueued to produce a TupleUpdate[A, B].
  *
  * If a left and right are enqueued when [[pop()]] is called, a [[BothUpdated]] is returned.
  *
  * This is useful when zipping two queues but without the one-each requirement.
  *
  * @param leftQueue
  * @param rightQueue
  * @tparam A
  * @tparam B
  */
private[join] class CombineQueue[A, B](leftQueue: FIFO[LeftUpdate[A, B]], rightQueue: FIFO[RightUpdate[A, B]])
    extends FIFO[Option[TupleUpdate[A, B]]]
    with StrictLogging {

  @volatile private var completed = false

  private object Lock

  @volatile private var leftCount  = 0
  @volatile private var rightCount = 0

  override def enqueue(value: Option[TupleUpdate[A, B]]): Boolean = {
    val result = Lock.synchronized {
      value match {
        case None =>
          completed = true
          Lock.notify()
          true
        case Some(left @ LeftUpdate(_)) =>
          val ok = leftQueue.enqueue(left)
          leftCount = leftCount + 1
          Lock.notify()
          ok
        case Some(right @ RightUpdate(_)) =>
          val ok = rightQueue.enqueue(right)
          rightCount = rightCount + 1
          Lock.notify()
          ok
        case Some(BothUpdated(a, b)) =>
          // the 'this should never happen' case
          Lock.notify()
          throw new IllegalStateException(s"A CombineQueue used to combine LeftUpdate and RightUpdate values has had a 'BothUpdated' enqueued w/ $a and $b")
      }
    }
    logger.debug(s"Enqueueing tuple $value has $leftCount and $rightCount, returning $result")
    result
  }

  // calls to pop should all be single-threaded
  override def pop(): Option[TupleUpdate[A, B]] = {
    Lock.synchronized {
      (leftCount, rightCount) match {
        case (a, b) if a > 0 && b > 0 =>
          val result = leftQueue.pop().and(rightQueue.pop().right)
          leftCount = leftCount - 1
          rightCount = rightCount - 1
          Option(result)
        case (a, _) if a > 0 =>
          val result = leftQueue.pop()
          leftCount = leftCount - 1
          Option(result)
        case (_, b) if b > 0 =>
          val result = rightQueue.pop()
          rightCount = rightCount - 1
          Option(result)
        case other =>
          if (completed && leftCount == 0 && rightCount == 0) {
            None
          } else {
            logger.debug(s"Combine queue waiting w/ $other")
            Lock.wait()
            pop()
          }
      }
    }
  }
}
