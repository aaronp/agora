package agora.rest.stream

import agora.BaseSpec
import agora.api.json.JsonSemigroup
import agora.api.streams.AsConsumerQueue.QueueArgs
import agora.api.streams.{BaseProcessor, BasePublisher}
import agora.rest.HasMaterializer
import io.circe.Json

/**
  * A DataUploadFlow represents some state where a single publisher is created at some point, and some subscribers
  * are attached at some other points, maybe, in any order.
  *
  * This is to support the REST service allowing us to dynamically create publishers/subscribers for a given name
  */
class DataUploadFlowTest extends BaseSpec with HasMaterializer {

  "DataUploadFlow.snapshot" should {
    "list the current queue sizes and pending requests" in {

      implicit val jsg = JsonSemigroup

      val newQueue = QueueArgs[String](None, None)
      val duf = new DataUploadFlow[QueueArgs, String]("name", newQueue)

      // listen to a test publisher
      val testPublisher = BaseProcessor.withMaxCapacity[String](100)
      testPublisher.subscribe(duf.DelegatingSubscriber)

      duf.UpstreamMessagePublisher



    }
  }

}
