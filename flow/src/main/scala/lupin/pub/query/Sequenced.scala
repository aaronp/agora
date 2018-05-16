package lupin.pub.query

import org.reactivestreams.Publisher

import scala.concurrent.ExecutionContext

case class Sequenced[T](seqNo: Long, data: T)

object Sequenced {

  def map[K, T, A](sequencedDataFeed: Publisher[(Long, (CrudOperation[K], T))])(getter: T => A)(implicit execContext: ExecutionContext): Publisher[Sequenced[(CrudOperation[K], A)]] = {
    import lupin.implicits._
    asRichPublisher(sequencedDataFeed).map {
      case (seqNo, (crudOp, input)) => Sequenced(seqNo, (crudOp, getter(input)))
    }
  }
}