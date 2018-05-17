package lupin.pub.query

import org.reactivestreams.Publisher

import scala.concurrent.ExecutionContext

case class Sequenced[T](seqNo: Long, data: T)

object Sequenced {

  def map[K, T, A](sequencedDataFeed: Publisher[((CrudOperation[K], T), Long)])(getter: T => A)(
      implicit execContext: ExecutionContext): Publisher[Sequenced[(CrudOperation[K], A)]] = {
    import lupin.implicits._
    sequencedDataFeed.map {
      case ((crudOp, input), seqNo) => Sequenced(seqNo, (crudOp, getter(input)))
    }
  }
}
