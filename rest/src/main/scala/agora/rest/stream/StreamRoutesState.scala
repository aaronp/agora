package agora.rest.stream

import agora.api.streams.AsConsumerQueue.QueueArgs
import akka.NotUsed
import akka.http.scaladsl.model.ws.Message
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax._

/**
  * Keeps track of registered publishers/subscribers
  *
  * @param initialUploadEntrypointByName
  */
private[stream] case class StreamRoutesState(initialUploadEntrypointByName: Map[String, DataUploadFlow[QueueArgs, Json]] = Map.empty) {

  private var uploadEntrypointByName = initialUploadEntrypointByName
  private var simpleSubscriberByName = Map[String, List[DataConsumerFlow[Json]]]()

  def subscriberKeys() = simpleSubscriberByName.keySet

  def uploadKeys() = uploadEntrypointByName.keySet

  def getUploadEntrypoint(name: String): Option[DataUploadFlow[QueueArgs, Json]] = uploadEntrypointByName.get(name)

  def getSimpleSubscriber(name: String) = simpleSubscriberByName.get(name)

  def newSimpleSubscriber(instance: DataConsumerFlow[Json])(implicit mat: Materializer): Flow[Message, Message, NotUsed] = {
    val newList = instance :: simpleSubscriberByName.getOrElse(instance.name, Nil)

    uploadEntrypointByName.get(instance.name).foreach { publisher =>
      publisher.delegatingPublisher.subscribe(instance)
    }

    simpleSubscriberByName = simpleSubscriberByName.updated(instance.name, newList)
    instance.flow
  }

  def snapshot(name: String): Json = {
    val consumerJson = {
      val jsonValues: Map[String, Json] = simpleSubscriberByName.mapValues { consumerList =>
        val snapshots: List[DataConsumerSnapshot] = consumerList.map(_.snapshot())
        snapshots.asJson
      }
      jsonValues.asJson
    }

    val publisherJson = uploadEntrypointByName.get(name).fold(Json.Null) { publisher =>
      publisher.snapshot().asJson
    }

    Map("upload" -> publisherJson, "consumers" -> consumerJson).asJson
  }

  def newUploadEntrypoint(sp: DataUploadFlow[QueueArgs, Json])(implicit mat: Materializer): Flow[Message, Message, NotUsed] = {
    uploadEntrypointByName.get(sp.name).foreach { old =>
      old.cancel()
    }

    simpleSubscriberByName.values.flatten.foreach { subscriber =>
      if (!sp.delegatingPublisher.containsSubscriber(subscriber)) {
        sp.delegatingPublisher.subscribe(subscriber)
      }
    }

    uploadEntrypointByName = uploadEntrypointByName.updated(sp.name, sp)
    sp.flow
  }
}
