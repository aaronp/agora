package agora.rest.stream

import agora.flow.AsConsumerQueue.QueueArgs
import akka.NotUsed
import akka.http.scaladsl.model.ws.Message
import akka.stream.Materializer
import akka.stream.scaladsl.Flow
import com.typesafe.scalalogging.StrictLogging
import io.circe.Json
import io.circe.generic.auto._
import io.circe.syntax._

/**
  * Keeps track of registered publishers/subscribers
  *
  * @param initialUploadEntrypointByName
  */
private[stream] case class StreamRoutesState(initialUploadEntrypointByName: Map[String, DataUploadFlow[QueueArgs, Json]] = Map.empty) extends StrictLogging {

  private var uploadEntrypointByName: Map[String, DataUploadFlow[QueueArgs, Json]] = initialUploadEntrypointByName
  private var simpleSubscriberByName                                               = Map[String, List[DataConsumerFlow[Json]]]()

  def subscriberKeys() = simpleSubscriberByName.keySet

  def uploadKeys() = uploadEntrypointByName.keySet

  def getUploadEntrypoint(name: String): Option[DataUploadFlow[QueueArgs, Json]] = uploadEntrypointByName.get(name)

  def getSimpleSubscriber(name: String): Option[List[DataConsumerFlow[Json]]] = simpleSubscriberByName.get(name)

  def newSimpleSubscriber(instance: DataConsumerFlow[Json])(implicit mat: Materializer): Flow[Message, Message, NotUsed] = {
    val newList = instance :: simpleSubscriberByName.getOrElse(instance.name, Nil)

    getUploadEntrypoint(instance.name).foreach { publisher =>
      publisher.republishingSubscriber.subscribe(instance.underlyingRepublisher)
    }

    simpleSubscriberByName = simpleSubscriberByName.updated(instance.name, newList)
    instance.flow
  }

  def snapshot(name: String): Json = {
    val publisherJson = getUploadEntrypoint(name).fold(Json.Null) { publisher =>
      publisher.snapshot().asJson
    }

    simpleSubscriberByName.get(name).fold(publisherJson) { consumerList =>
      val snapshots: List[DataConsumerSnapshot] = consumerList.map(_.snapshot())
      val jsonList                              = Map("_pendingSubscribers" -> snapshots.asJson).asJson
      publisherJson.deepMerge(jsonList)
    }
  }

  def newUploadEntrypoint(dataUpload: DataUploadFlow[QueueArgs, Json])(implicit mat: Materializer): Flow[Message, Message, NotUsed] = {
    getUploadEntrypoint(dataUpload.name).foreach { old =>
      old.cancel()
    }

    simpleSubscriberByName.get(dataUpload.name) match {
      case None =>
        logger.debug(s"No subscribers found pending for '${dataUpload.name}'")
      case Some(pendingSubscribers) =>
        logger.debug(s"Adding ${pendingSubscribers.size} subscribers to '${dataUpload.name}'")
        pendingSubscribers.foreach { subscriber =>
          val contains = dataUpload.republishingSubscriber.containsSubscriber(subscriber.underlyingRepublisher)
          if (!contains) {
//            the problem here is that pending 'takeNext' control messages aren't sent down as we
//            publish them to nobody   ... the upload 'republishingSubscriber' doesn't

            dataUpload.republishingSubscriber.subscribe(subscriber.underlyingRepublisher)
          } else {
            logger.debug(s"'${dataUpload.name}' Already contained $subscriber")
          }
        }
    }

    uploadEntrypointByName = uploadEntrypointByName.updated(dataUpload.name, dataUpload)
    dataUpload.flow
  }
}
