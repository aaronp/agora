package agora.rest.stream

import akka.stream.Materializer
import io.circe.Json

/**
  * Keeps track of the upload and consumer subscriptions
  *
  * @param initialuploadEntrypointByName
  */
private[stream] case class StreamRoutesState(
    initialuploadEntrypointByName: Map[String, DataUploadFlow[Json]] = Map.empty
) {
  var uploadEntrypointByName = initialuploadEntrypointByName
  var simpleSubscriberByName = Map[String, List[DataConsumerFlow[Json]]]()

  def getUploadEntrypoint(name: String): Option[DataUploadFlow[Json]] = uploadEntrypointByName.get(name)

  def getSimpleSubscriber(name: String) = simpleSubscriberByName.get(name)

  def newSimpleSubscriber(instance: DataConsumerFlow[Json])(implicit mat: Materializer) = {
    val newList = instance :: simpleSubscriberByName.getOrElse(instance.name, Nil)

    uploadEntrypointByName.get(instance.name).foreach { publisher =>
      publisher.delegatingPublisher.subscribe(instance)
    }

    simpleSubscriberByName = simpleSubscriberByName.updated(instance.name, newList)
    instance.flow
  }

  def newUploadEntrypoint(sp: DataUploadFlow[Json])(implicit mat: Materializer) = {
    uploadEntrypointByName.get(sp.name).foreach { old =>
      old.cancel()
    }

    simpleSubscriberByName.values.flatten.foreach { subscriber =>
      if (!sp.delegatingPublisher.isSubscribed(subscriber.name)) {
        sp.delegatingPublisher.subscribe(subscriber)
      }
    }

    uploadEntrypointByName = uploadEntrypointByName.updated(sp.name, sp)
    sp.flow
  }
}
