package lupin.pub.query

import lupin.Publishers
import lupin.example.{IndexRange, IndexSelection, SpecificIndices}
import lupin.pub.sequenced.DurableProcessor
import lupin.sub.BaseSubscriber
import org.reactivestreams.{Publisher, Subscriber, Subscription}

/**
  * A publisher which can create subscriptions using some input data
  */
trait QueryPublisher[Q, T] extends Publisher[T] {

    protected def defaultInput: Q

    def subscribeWith(input: Q, subscriber: Subscriber[_ >: T]): Unit

    /**
      * The default is to start subscribing from the first available index
      *
      * @param subscriber
      */
    override def subscribe(subscriber: Subscriber[_ >: T]) = subscribeWith(defaultInput, subscriber)

}

object QueryPublisher {

  def apply[T :Ordering]() = {
    ???
  }

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
  class Indexer[T: Ordering](override val defaultInput: IndexSelection) extends QueryPublisher[IndexSelection, (T, Int)] with BaseSubscriber[T]  {

    private var values = Vector[T]()
    private val publisher = DurableProcessor[(T, Int)]()

    def upsert(input: T): (T, Int) = {
      if (!values.contains(input)) {
        values = values :+ input
      }
      val idx = values.indexOf(input)
      val pear = (input, idx)
      publisher.onNext(pear)
      pear
    }


    override def onSubscribe(s: Subscription): Unit = {
      publisher.onSubscribe(s)
    }

    override def subscribeWith(input: IndexSelection, subscriber: Subscriber[_ >: (T, Int)]): Unit = {
      input match {
        case SpecificIndices(indices) =>
        case IndexRange(from, to) =>
      }
      Publishers.forList()
      publisher.subscribe(subscriber)
    }

    override def onNext(value: T): Unit = {
      upsert(value)
    }
  }

}