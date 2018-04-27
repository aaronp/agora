package lupin.example

import lupin.pub.query.QueryPublisher
import org.reactivestreams.{Publisher, Subscriber}

object FieldFeed {

  class Instance[ID, T](dao : IndexedOrder[(ID, T)], override val defaultInput: IndexSelection) extends QueryPublisher[IndexSelection, FieldUpdate[ID]] {

    override def subscribeWith(query: IndexSelection, subscriber: Subscriber[_ >: FieldUpdate[ID]]) = {
      val pub: Publisher[(ID, T)] = dao.valuesAtIndex(query)

      ???

    }
  }

  def apply[ID, T](dao : IndexedOrder[(ID, T)],
                   defaultInput: IndexSelection = IndexSelection(0, 10)): QueryPublisher[IndexSelection, FieldUpdate[ID]] = {
    new Instance[ID, T](dao, defaultInput)

  }

}
