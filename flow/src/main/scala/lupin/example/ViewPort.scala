package lupin.example

import lupin.Subscribers
import lupin.pub.query.SyncDao
import lupin.pub.sequenced.{DurableProcessor, DurableProcessorInstance}
import org.reactivestreams.{Publisher, Subscriber}

import scala.concurrent.ExecutionContext.Implicits._

trait ConcreteExample {

  case class Person(name: String, age: Int, userId: Long)

  type PeopleDao = SyncDao[Long, Person]

  //  case class SentViewState(dao: PeopleDao,
  //                           totalReceived: Long,
  //                           lastView: Option[ViewPort[Person]]) {
  //    // given a viewport, we should be able to work out which updates we need to send
  //    def resolve(view: ViewPort[Person]): List[Long] = {
  //      ???
  //    }
  //  }

  def run[K, Input](dataStream: Publisher[Input])(implicit lookup: SyncDao[K, Input]): DurableProcessorInstance[SyncDao[K, Input]] = {

    val daoFeed: DurableProcessorInstance[SyncDao[K, Input]] = DurableProcessor[SyncDao[K, Input]]()

    val listener: Subscriber[Input] = Subscribers.fold[SyncDao[K, Input], Input](daoFeed, lookup) { (s, input) =>
      val (_, newDao) = s.update(input)
      newDao
    }
    dataStream.subscribe(listener)
    daoFeed
  }
}
