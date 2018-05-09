package lupin.pub.query

import lupin.Publishers
import lupin.example.{IndexRange, IndexSelection, SpecificIndices}
import lupin.pub.sequenced.{DurableProcessor, DurableProcessorDao, DurableProcessorInstance}
import lupin.sub.BaseSubscriber
import org.reactivestreams.{Subscriber, Subscription}

import scala.concurrent.ExecutionContext

/** The idea of an 'Indexer' is something which indexes a property A of some type T.
  *
  * We'll have some original publisher of 'T' which gets mapped onto a key/value of [K, V],
  * but for our purposes that can just be represented as a single type 'A' (e.g. A is [K,V])
  *
  * For this naive implementation, we just need an Ordering[A] to work out which index, then republish the (A, Index) ...
  * which will in practice be the index, key and field value.
  *
  * @tparam T
  */
class Indexer[T: Ordering](override val defaultInput: IndexSelection)(implicit execContext: ExecutionContext)
    extends QueryPublisher[IndexSelection, (T, Int)]
    with BaseSubscriber[T] {

  private var values                                        = Vector[T]()
  private val publisher: DurableProcessorInstance[(T, Int)] = DurableProcessor[(T, Int)]()

  def upsert(input: T): (T, Int) = {
    if (!values.contains(input)) {
      values = values :+ input
    }
    val idx  = values.indexOf(input)
    val pear = (input, idx)
    publisher.onNext(pear)
    pear
  }

  override def onSubscribe(s: Subscription): Unit = {
    publisher.onSubscribe(s)
  }

  override def subscribeWith(input: IndexSelection, subscriber: Subscriber[_ >: (T, Int)]): Unit = {
    val queryPub: DurableProcessorInstance[(T, Int)] = Publishers(DurableProcessorDao[(T, Int)]())

    val indicesIter: Iterator[Long] = input match {
      case SpecificIndices(indices) => indices.iterator
      case IndexRange(from, to)     => (from.to(to)).filter(_ <= Int.MaxValue).iterator
    }

    val lastReceived = publisher.lastIndex()
    val get          = values.lift
    indicesIter.foreach { idxLong =>
      if (idxLong <= Int.MaxValue) {
        get(idxLong.toInt).foreach { value =>
          queryPub.onNext(value -> idxLong.toInt)
        }
      }
    }
    queryPub.onComplete()

    Publishers
      .concat(queryPub) { sub =>
        lastReceived match {
          case None      => publisher.subscribe(sub)
          case Some(idx) => publisher.subscribeFrom(idx, sub)
        }
      }
      .subscribe(subscriber)
  }

  override def onNext(value: T): Unit = {
    upsert(value)
  }
}
