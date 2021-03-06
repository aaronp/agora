package agora.api.streams

import java.util.concurrent.locks.ReentrantLock

import _root_.io.circe.syntax._
import _root_.io.circe.{Encoder, Json}
import agora.json.{JPath, JsonDiff, JsonDiffAsDataDiff, TypesByPath, TypesByPathSemigroup}
import agora.api.streams.JsonFeedDsl.{IndexSubscriber, JsonDeltaSubscriber, JsonFieldSubscriber}
import agora.api.streams.PublisherOps.implicits._
import agora.flow._
import agora.core.{DataDiff, IsEmpty}
import cats.Semigroup
import org.reactivestreams.Publisher

/**
  * DSL for putting things on an upstream json feed (publisher)
  *
  * @param underlyingPublisher
  */
class JsonFeedDsl(override protected val underlyingPublisher: Publisher[Json]) extends HasPublisher[Json] {

  def withFields(maxQueueSize: Int): JsonFieldSubscriber[AsConsumerQueue.MaxCapacity] = {
    //    withFields(() => ConsumerQueue.withMaxCapacity(maxQueueSize))
    withFields[AsConsumerQueue.MaxCapacity](AsConsumerQueue.MaxCapacity[TypesByPath](maxQueueSize))
  }

  /**
    * A field subscription which conflates fields
    *
    * @return a field subscription
    */
  def withFields(): JsonFieldSubscriber[Semigroup] = {
    //    val s : Semigroup[Json] = JsonSemigroup
    withFields[Semigroup](TypesByPathSemigroup) //(AsConsumerQueue.SemigroupAsQueue)
  }

  /**
    * @return a field subscription with a custom queue
    */
  def withFields[F[_]](newQueueInput: F[TypesByPath])(implicit asConsumerQueue: AsConsumerQueue[F]): JsonFieldSubscriber[F] = {
    val pathSubscriber: FieldFeed.AccumulatingJsonPathsSubscriber[F] = new FieldFeed.AccumulatingJsonPathsSubscriber(newQueueInput)
    underlyingPublisher.subscribe(pathSubscriber)
    new JsonFieldSubscriber(underlyingPublisher, pathSubscriber)
  }

  /**
    * route feeds based on an index created by the value represented by the given path
    *
    * @param path    the path to the value on which the keys should be taken
    * @param theRest any other paths to construct a constituent key
    */
  def indexOnKeys(path: JPath, theRest: JPath*)(newQ: => ConsumerQueue[Json]): IndexSubscriber = {

    def newPublisher(indexSubscriber: IndexSubscriber, key: List[Json]): BaseProcessor[Json] = {
      new BaseProcessor[Json] with IntKeyedPublisher[Json] {
        override def onRequestNext(subscription: BasePublisher.BasePublisherSubscription[Json], requested: Long) = {
          val nrToTake = super.onRequestNext(subscription, requested)
          if (nrToTake > 0) {
            indexSubscriber.request(nrToTake)
          }
          nrToTake
        }

        override def toString: String = key.mkString("[", ",", "[")

        override def newDefaultSubscriberQueue() = newQ
      }
    }

    indexOnKeys(path :: theRest.toList, newPublisher _)
  }

  /**
    * route feeds based on an index created by the value represented by the given path
    *
    * @param paths              the path to the value on which the keys should be taken
    * @param newPublisherForKey a means to create a new publisher for data with the given key(s)
    */
  def indexOnKeys(paths: List[JPath],
                  newPublisherForKey: (IndexSubscriber, List[Json]) => BaseProcessor[Json],
                  initialRequest: Int = 0,
                  name: String = ""): IndexSubscriber = {
    val actualName = name match {
      case "" => paths.mkString("Index on [", ",", "]")
      case n  => n
    }
    val subscriber = new IndexSubscriber(actualName, paths, initialRequest, newPublisherForKey)
    underlyingPublisher.subscribe(subscriber)
    subscriber
  }

  /** listen for deltas
    *
    * @param mkQueue
    * @param initialRequest
    * @param diff
    * @return
    */
  def withDeltas(mkQueue: () => ConsumerQueue[JsonDiff] = () => ConsumerQueue(None), initialRequest: Int = 0)(implicit diff: DataDiff[Json, JsonDiff] =
                                                                                                                JsonDiffAsDataDiff): JsonDeltaSubscriber = {

    object DownstreamPublisher extends JsonDeltaSubscriber {

      override def onRequestNext(subscription: BasePublisher.BasePublisherSubscription[JsonDiff], requested: Long) = {
        val nrToTake = super.onRequestNext(subscription, requested)
        if (nrToTake > 0) {
          request(nrToTake)
        }
        nrToTake
      }

      override def newDefaultSubscriberQueue(): ConsumerQueue[JsonDiff] = mkQueue()
    }

    implicit val diffEmpty: IsEmpty[JsonDiff] = agora.json.JsonDiffIsEmpty
    underlyingPublisher.subscribeToDeltas(DownstreamPublisher)
    DownstreamPublisher
  }

}

object JsonFeedDsl {

  def apply(publisher: Publisher[Json]) = new JsonFeedDsl(publisher)

  trait LowPriorityJsonFeedImplicits {
    implicit def asJsonFeed(pub: BasePublisher[Json]) = new JsonFeedDsl(pub)
  }

  object implicits extends LowPriorityJsonFeedImplicits

  /**
    * A Subscription which will in turn publish data per key, where the key is the value(s) determined by the JPaths
    *
    * @param name
    * @param initialRequest
    * @param newPublisherForKey the factory to use when creating a new publisher
    */
  class IndexSubscriber(name: String, paths: List[JPath], initialRequest: Int, newPublisherForKey: (IndexSubscriber, List[Json]) => BaseProcessor[Json])
      extends BaseSubscriber[Json] {
    type Key = List[Json]
    private val Lock                                          = new ReentrantLock()
    private var publisherByKey: Map[Key, BaseProcessor[Json]] = Map.empty

    def getPublisher[K: Encoder](key: K): Publisher[Json] = {
      getPublisher(List(key.asJson))
    }

    def getPublisher(key: Key): Publisher[Json] = getOrCreateBasePublisher(key)

    private def getOrCreateBasePublisher(key: Key): BaseProcessor[Json] = {
      publisherByKey.get(key) match {
        case Some(p) => p
        case None =>
          Lock.lockInterruptibly()
          try {
            val p = newPublisherForKey(this, key)
            publisherByKey = publisherByKey.updated(key, p)
            p
          } finally {
            Lock.unlock()
          }

      }
    }

    override def onNext(json: Json): Unit = {
      val key = paths.map { path =>
        path(json).getOrElse(Json.Null)
      }
      getOrCreateBasePublisher(key).publish(json)
      request(1)
    }
  }

  /**
    * A Subscription which takes Json --> JsonDiff to downstream publisher
    *
    */
  trait JsonDeltaSubscriber extends BaseSubscriber[Either[Json, JsonDiff]] with BasePublisher[JsonDiff] {
    private var latestJson: Json = Json.Null

    def lastDeltaJson(): Json = {
      lastDiff match {
        case Some(delta) => delta.strip(latestJson)
        case None        => latestJson
      }
    }

    private var lastDiff: Option[JsonDiff] = None

    override def onNext(either: Either[Json, JsonDiff]): Unit = {
      either match {
        case Left(json) =>
          latestJson = json
          publish(JsonDiff(json))
        case Right(diff) =>
          lastDiff = Option(diff)
          publish(diff)
      }
      request(1)
    }
  }

  class JsonFieldSubscriber[F[_]](publisher: Publisher[Json], override protected val underlyingSubscriber: FieldFeed.AccumulatingJsonPathsSubscriber[F])
      extends HasSubscriber[Json] {

    def request(n: Int) = underlyingSubscriber.request(n)

    def fieldPublisher: Publisher[TypesByPath] = underlyingSubscriber.pathPublisher

    def fields: TypesByPath = underlyingSubscriber.fields
  }

}
