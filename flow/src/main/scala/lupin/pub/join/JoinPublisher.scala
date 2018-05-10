package lupin.pub.join

import lupin.Publishers
import lupin.data.HasKey
import lupin.pub.FIFO
import lupin.pub.collate.CollatingPublisher
import lupin.pub.passthrough.PassthroughPublisher
import org.reactivestreams.{Publisher, Subscriber}

import scala.concurrent.ExecutionContext

object JoinPublisher {

  def apply[A, B](left: Publisher[A], right: Publisher[B])(implicit ec: ExecutionContext): Publisher[TupleUpdate[A, B]] = {
    apply(left, right, () => FIFO[LeftUpdate[A, B]](), () => FIFO[RightUpdate[A, B]]())
  }

  /** Like a [[CollatingPublisher]], but requests through a join publisher will split requests between the left
    * and the right publishers.
    *
    * If one side publishes faster than the other, the joined publisher will poop out either [[RightUpdate]] or [[LeftUpdate]].
    *
    * If both publishers poop out a value before this publisher consumes a value, then it will be a [[BothUpdated]]
    *
    * @param left
    * @param right
    * @param ec
    * @tparam A
    * @tparam B
    * @return a publisher which joins the two publishers
    */
  def apply[A, B](left: Publisher[A],
                  right: Publisher[B],
                  newLeftQueue: () => FIFO[LeftUpdate[A, B]],
                  newRightQueue: () => FIFO[RightUpdate[A, B]]
                 )(implicit ec: ExecutionContext): Publisher[TupleUpdate[A, B]] = {

    //
    // first, create summat which will request from both publishers
    //
    val collate = CollatingPublisher[Int, TupleUpdate[A, B]](fair = true)

    val fromLeft = collate.newSubscriber(1)
    Publishers.map(left)(a => TupleUpdate.left[A, B](a)).subscribe(fromLeft)
    fromLeft.request(1)


    val fromRight = collate.newSubscriber(2)
    Publishers.map(right)(b => TupleUpdate.right[A, B](b)).subscribe(fromRight)
    fromRight.request(1)

    //
    // now subscribe a subscriber-side passthrough publisher which will collate the values
    //
    val consumer = PassthroughPublisher[TupleUpdate[A, B]](() => new CombineQueue(newLeftQueue(), newRightQueue()))
    collate.valuesPublisher.subscribe(consumer)

    consumer
  }


}
