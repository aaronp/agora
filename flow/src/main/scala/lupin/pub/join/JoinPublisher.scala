package lupin.pub.join

import lupin.Publishers
import lupin.pub.collate.CollatingPublisher
import lupin.pub.impl.HasKey
import lupin.pub.sequenced.DurableProcessor
import lupin.sub.BaseSubscriber
import org.reactivestreams.{Publisher, Subscriber}

import scala.concurrent.ExecutionContext

object JoinPublisher {

  /**
    *
    * @param left
    * @param right
    * @param ec
    * @tparam A
    * @tparam B
    * @return
    */
  def apply[A, B](left : Publisher[A], right : Publisher[B])(implicit ec : ExecutionContext) : Publisher[TupleUpdate[A, B]]= {
    val processor = DurableProcessor[TupleUpdate[A, B]]()

    val collate = CollatingPublisher[Int, Either[A,B]]()

    val fromLeft: Subscriber[Either[A, B]] with HasKey[Int] = collate.newSubscriber(1)
    Publishers.map(left)(a => Left(a)).subscribe(fromLeft)

    val fromRight: Subscriber[Either[A, B]] with HasKey[Int] = collate.newSubscriber(2)
    Publishers.map(right)(b => Right(b)).subscribe(fromRight)


    // instead of 'map', we could 'fold' a value into the publisher, if we had the concept of 'single-subscription'
    // publishers
    Publishers.map(collate) {
        case (1, Left(a)) =>
        case (2, Right(b)) =>
    }
    ???
  }
}
