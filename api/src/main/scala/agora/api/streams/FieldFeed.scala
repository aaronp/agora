package agora.rest.stream

import agora.api.json.{TypeNode, TypesByPath, newTypesByPath}
import agora.api.streams.{AccumulatingSubscriber, BaseProcessor, ConsumerQueue}
import io.circe.Json

/**
  * A means to query which json fields this subscriber has seen
  */
trait FieldFeed {
  def fields: TypesByPath
}

object FieldFeed {

  class AccumulatingJsonPathsSubscriber(newQ: () => ConsumerQueue[TypesByPath])
      extends AccumulatingSubscriber[Json, TypesByPath](newTypesByPath())
      with FieldFeed {

    val pathPublisher = BaseProcessor[TypesByPath](newQ)

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

  def apply(initialFieldRequest: Int = 1) = new AccumulatingJsonPathsSubscriber(() => ConsumerQueue(initialFieldRequest))

}
