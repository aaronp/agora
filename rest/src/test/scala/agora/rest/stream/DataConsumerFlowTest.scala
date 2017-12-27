package agora.rest.stream

import agora.BaseSpec
import agora.api.streams.JsonFeedDsl.implicits._
import agora.api.streams.{BaseProcessor, JsonFeedDsl}
import agora.rest.HasMaterializer
import agora.rest.exchange.ClientSubscriptionMessage
import akka.NotUsed
import akka.stream.scaladsl.Flow
import io.circe.Json

class DataConsumerFlowTest extends BaseSpec with HasMaterializer {
  "DataConsumerFlow.join" should {
    "be able to join two flows" in {
      case class Data(x: Int, y: Int)

      val baseProcessor: BaseProcessor[Json] = BaseProcessor.withMaxCapacity[Json](100)

      val baseFlow = DataConsumerFlow("test", baseProcessor)

      // create one subscriber who will directly subscribe to our baseProcessor
      val fieldSubscriberCheck = baseProcessor.withFields

      // ... and one which will be joined
      val joinedProcessor                                    = BaseProcessor.withMaxCapacity[Json](100)
      val joiedFields                                        = joinedProcessor.withFields
      val a: DataConsumerFlow[Json]                          = DataConsumerFlow("joined", joinedProcessor)

//      baseFlow.join(tf)
    }
  }
}
