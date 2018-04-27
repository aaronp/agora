package lupin.pub.join

import lupin.pub.sequenced.DurableProcessor
import lupin.sub.BaseSubscriber
import org.reactivestreams.Publisher

import scala.concurrent.ExecutionContext

object JoinPublisher {

  def apply[A, B](left : Publisher[A], right : Publisher[B])(implicit ec : ExecutionContext) : Publisher[TupleUpdate[A, B]]= {
    val processor = DurableProcessor[TupleUpdate[A, B]]()

    object FromLeft extends BaseSubscriber[A] {
      override def onNext(t: A): Unit = {

      }
    }
    left.subscribe(FromLeft)
    FromLeft.request(1)

    object FromRight extends BaseSubscriber[B] {
      override def onNext(t: B): Unit = {

      }
    }
    FromRight.request(1)
    right.subscribe(FromRight)

    processor
  }
}
