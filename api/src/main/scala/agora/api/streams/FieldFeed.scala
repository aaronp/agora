package agora.api.streams

import agora.json.{TypeNode, TypesByPath, newTypesByPath}
import agora.flow.{AccumulatingSubscriber, AsConsumerQueue, BaseProcessor}
import io.circe.Json

/**
  * A means to query which json fields this subscriber has seen
  */
trait FieldFeed {
  def fields: TypesByPath
}

object FieldFeed {

  class AccumulatingJsonPathsSubscriber[F[_]](newQueueInput: F[TypesByPath])(implicit asConsumerQueue: AsConsumerQueue[F])
      extends AccumulatingSubscriber[Json, TypesByPath](newTypesByPath())
      with FieldFeed {

    val pathPublisher = BaseProcessor[F, TypesByPath](newQueueInput)(asConsumerQueue)

    override protected def combine(lastState: TypesByPath, delta: Json) = {
      val deltaPaths: TypesByPath = TypeNode(delta).flattenPaths
      val newPaths                = deltaPaths.filterNot(lastState.contains)
      if (newPaths.nonEmpty) {
        val newState: TypesByPath = lastState ++ newPaths
        pathPublisher.onNext(newState)
        newState
      } else {
        lastState
      }
    }

    override def fields = state
  }

  def apply[F[_]](newQueueInput: F[TypesByPath])(implicit asConsumerQueue: AsConsumerQueue[F]): AccumulatingJsonPathsSubscriber[F] = {
    new AccumulatingJsonPathsSubscriber(newQueueInput)
  }

}
