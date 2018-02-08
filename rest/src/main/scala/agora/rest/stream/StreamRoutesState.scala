package agora.rest.stream

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
private[stream] case class StreamRoutesState(initialUploadEntrypointByName: Map[String, SocketPipeline.DataSubscriber[Json]] = Map.empty) extends StrictLogging {

  private var uploadEntrypointByName: Map[String, SocketPipeline.DataSubscriber[Json]] = initialUploadEntrypointByName
  private var simpleSubscriberByName = Map[String, List[SocketPipeline.DataPublisher[Json]]]()

  def subscriberKeys() = simpleSubscriberByName.keySet

  def uploadKeys() = uploadEntrypointByName.keySet

  def getUploadEntrypoint(name: String): Option[SocketPipeline.DataSubscriber[Json]] = uploadEntrypointByName.get(name)

  def getSimpleSubscriber(name: String) = simpleSubscriberByName.get(name)

  def newSimpleSubscriber(name: String)(implicit mat: Materializer): Option[Flow[Message, Message, NotUsed]] = {

    getUploadEntrypoint(name).map { dataUploadFlow: SocketPipeline.DataSubscriber[Json] =>

      // TODO - use a different exec context for DAOs
      import mat.executionContext

      val dataSubscriber = SocketPipeline.DataPublisher[Json](dataUploadFlow.republishingDataConsumer)

      val newList: List[SocketPipeline.DataPublisher[Json]] = dataSubscriber :: simpleSubscriberByName.getOrElse(name, Nil)



      // this is the bit which will consume from the publisher, and republish to N subscribers.
      // //subscribed to the flow
      //      dataSubscriber.republishingDataConsumer


      simpleSubscriberByName = simpleSubscriberByName.updated(name, newList)
      //      dataUploadFlow.localPublisher.subscribe(dataSubscriber.republishingDataConsumer)
      dataSubscriber.flow
    }
  }

  def snapshot(name: String): Json = {
    val publisherJson = getUploadEntrypoint(name).fold(Json.Null) { publisher: SocketPipeline.DataSubscriber[Json] =>
//      publisher.snapshot().asJson
      Json.Null
    }

    simpleSubscriberByName.get(name).fold(publisherJson) { consumerList =>
      val snapshots = consumerList.map(_.snapshot())
      val jsonList = Map("_pendingSubscribers" -> snapshots.asJson).asJson
      publisherJson.deepMerge(jsonList)
    }
  }

  def newUploadEntrypoint(name: String, dataUpload: SocketPipeline.DataSubscriber[Json])(implicit mat: Materializer): Flow[Message, Message, NotUsed] = {
    getUploadEntrypoint(name).foreach { old =>
      old.cancel()
    }

    // TODO - in order to support subscriptions before there are publishers, we'd have to add a level of indirection,
    // an intermediate publisher to create the flow

    //    simpleSubscriberByName.get(name) match {
    //      case None =>
    //        logger.debug(s"No subscribers found pending for '${name}'")
    //      case Some(pendingSubscribers) =>
    //        logger.debug(s"Adding ${pendingSubscribers.size} subscribers to '${name}'")
    //        pendingSubscribers.foreach { subscriber =>
    //          //          val contains = dataUpload.republishingSubscriber.containsSubscriber(subscriber.underlyingRepublisher)
    //          //          if (!contains) {
    //          //            the problem here is that pending 'takeNext' control messages aren't sent down as we
    //          //            publish them to nobody   ... the upload 'republishingSubscriber' doesn't
    //
    //          dataUpload.localPublisher.subscribe(subscriber.republishingDataConsumer)
    //          //          } else {
    //          //            logger.debug(s"'${dataUpload.name}' Already contained $subscriber")
    //          //          }
    //        }
    //    }

    uploadEntrypointByName = uploadEntrypointByName.updated(name, dataUpload)
    dataUpload.flow
  }
}
