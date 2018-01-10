package agora.api.streams

import agora.api.streams.PublisherOps.implicits._
import io.circe.Encoder

import scala.reflect.ClassTag

/**
  * This is the root entry point for pushing data through a stream.
  *
  * We start here at some 'T' --> Json
  *
  * It doesn't strictly need to be json (that just happens to be the first supported, easiest),
  * but would work for any other target type with the right type-classes (e.g. [[agora.io.core.FieldSelector]],
  * [[agora.io.core.IsEmpty]], [[agora.io.core.DataDiff]].
  *
  * @param underlyingProcessor
  * @tparam T
  */
class DataFeedDsl[T: ClassTag](override val underlyingProcessor: BaseProcessor[T]) extends HasProcessor[T, T] {

  def asJsonDsl(implicit asJson: Encoder[T]): JsonFeedDsl = {
    val jsonPublisher = underlyingProcessor.map(asJson.apply)
    new JsonFeedDsl(jsonPublisher)
  }
}

object DataFeedDsl {
  def withMaxCapacity[T: ClassTag](maxCapacity: Int = 10000) = {
    new DataFeedDsl[T](BaseProcessor.withMaxCapacity[T](maxCapacity))
  }
}
