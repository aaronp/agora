package lupin.example

import lupin.pub.query.QueryPublisher
import lupin.{Publishers, Subscribers}
import org.reactivestreams.Publisher

object Indexer {

  class Dao[T] extends QueryPublisher[]

  /**
    * Made to fold an indexing DAO which supports a QueryPublisher over a stream of values 'T'.
    *
    * @param publisher
    * @tparam T
    * @return
    */
  def apply[T](publisher: Publisher[T]) = {
    Subscribers.fold()
   ???
  }

}
