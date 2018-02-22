package agora.rest.stream

import agora.BaseSpec
import agora.flow.{DurableProcessor, DurableProcessorDao, ListSubscriber}
import agora.rest.exchange.ClientSubscriptionMessage
import io.circe.Json
import org.reactivestreams.{Publisher, Subscriber, Subscription}
import org.scalatest.GivenWhenThen
import org.scalatest.concurrent.Eventually

import _root_.scala.concurrent.ExecutionContext.Implicits._

class SocketPipelineTest extends BaseSpec with GivenWhenThen with Eventually {

  "Socket.DataSubscriber" should {

    "republish uploaded data" in {
      Given("A server-side remote subscriber pipeline")
      val pipeline = SocketPipeline.DataSubscriber[Json]()

      And("A means to observe the pipelines published control messages")
      val controlMsgObserver = new ListSubscriber[ClientSubscriptionMessage]
      pipeline.controlMessagePublisher.subscribe(controlMsgObserver)
      controlMsgObserver.request(10)

      When("A the remote subscriber subscribes to some upload publisher")
      val clientUpload = DurableProcessor[Json]()
      clientUpload.subscribe(pipeline.republishingDataConsumer)

      Then("no control messages should be sent as nothing is pulling from the republishing processor")
      controlMsgObserver.received() shouldBe Nil

      When("A subscriber requests data from the pipeline republisher")
      val publishedListener1 = new ListSubscriber[Json]
      pipeline.republishingDataConsumer.subscribe(publishedListener1)
      publishedListener1.request(7)

      Then("A control message should be sent")
      eventually {
        controlMsgObserver.received() shouldBe List(TakeNext(7))
      }

      When("The data source uploads some data")
      val firstMsg = json"""{ "hello" : "world" }"""
      clientUpload.onNext(firstMsg)
      val secondMsg = json"""{ "second" : "msg" }"""
      clientUpload.onNext(secondMsg)

      Then("The data should be republished")
      eventually {
        publishedListener1.receivedInOrderReceived() shouldBe List(firstMsg, secondMsg)
      }

      When("A new subscriber requests data from the pipeline republisher at index 1")
      val publishedListener2 = new ListSubscriber[Json]
      pipeline.republishingDataConsumer.subscribeFrom(1, publishedListener2)
      publishedListener2.request(9)

      Then("The new subscriber should receive the second message")
      eventually {
        publishedListener2.receivedInOrderReceived() shouldBe List(secondMsg)
      }

      And("another request control message should be sent")
      withClue("as we started from index 1, requesting 9 would require taking up to 10 elements (leaving 3 to pull as we already requested 7).") {
        eventually {
          controlMsgObserver.received() shouldBe List(TakeNext(3), TakeNext(7))
        }
      }
    }

    "publish TakeNext messages when the local subscriber requests more work" in {
      Given("A local subscriber")
      val localSubscriber = new ListSubscriber[String]
      val pipeline        = SocketPipeline.DataSubscriber[String](DurableProcessorDao[String]())
      pipeline.republishingDataConsumer.subscribe(localSubscriber)

      // listen to the client messages produced from the pipeline
      val controlMsgSubscriber = new ListSubscriber[ClientSubscriptionMessage]
      pipeline.controlMessagePublisher.subscribe(controlMsgSubscriber)
      controlMsgSubscriber.request(3)

      When("the local subscriber is subscribed")
      val source = DurableProcessor[String]()
      source.subscribe(pipeline.republishingDataConsumer)

      And("The local subscriber requests some elements")
      localSubscriber.request(32)

      Then("The pipeline should publish a TakeNext message")
      eventually {
        controlMsgSubscriber.received() shouldBe List(TakeNext(32))
      }
    }
    "publish Cancel messages when the local subscriber cancels" in {
      Given("A local subscriber")
      val localSubscriber = new ListSubscriber[String]
      val pipeline        = SocketPipeline.DataSubscriber[String]()
      pipeline.republishingDataConsumer.subscribe(localSubscriber)

      // listen to the client messages produced from the pipeline
      val controlMsgSubscriber = new ListSubscriber[ClientSubscriptionMessage]
      pipeline.controlMessagePublisher.subscribe(controlMsgSubscriber)
      controlMsgSubscriber.request(3)

      When("the local subscriber is subscribed")
      val source = DurableProcessor[String]()
      source.subscribe(pipeline.republishingDataConsumer)

      And("The local subscriber cancels")
      localSubscriber.cancel()

      Then("The pipeline NOT should publish a Cancel message")
      controlMsgSubscriber.received() shouldBe Nil
      Thread.sleep(10)
      controlMsgSubscriber.received() shouldBe Nil

      When("The republishingDataConsumer cancels")
      pipeline.republishingDataConsumer.cancel()

      Then("A Cancel control message should be sent")
      eventually {
        controlMsgSubscriber.received() shouldBe List(Cancel)
      }
    }
  }
  "Socket.DataPublisher" should {
    "notify its subscribers when complete" is (pending)
    "notify its subscribers on error" is (pending)
    "not pull from its publisher until requested via the controlMessageProcessor" in {

      Given("A SocketPipeline wrapping a local publisher")
      val localPublisher = new TestPublisher[String]
      val pipeline       = SocketPipeline.DataPublisher(localPublisher)
      localPublisher.requests shouldBe Nil

      When("A subscriber requests from the flowProcessor")
      // request from the flowProcessor, which is what akka io will do
      val serverListener = new ListSubscriber[String]
      pipeline.flowProcessor.subscribe(serverListener)
      serverListener.request(16)

      Then("Nothing should yet pull from the local publisher")
      localPublisher.requests shouldBe Nil

      When("An explicit TakeNext message is received for N")
      val controlMessagePublisher = DurableProcessor[ClientSubscriptionMessage]()
      controlMessagePublisher.subscribe(pipeline.controlMessageProcessor)
      controlMessagePublisher.onNext(TakeNext(4))

      Then("N should be requested from the local publisher")
      eventually {
        localPublisher.requests shouldBe List(4)
      }

      And("again...")
      controlMessagePublisher.onNext(TakeNext(100))
      controlMessagePublisher.onNext(TakeNext(19))
      eventually {
        localPublisher.requests shouldBe List(19, 100, 4)
      }

    }
  }

  class TestPublisher[T] extends Publisher[T] with Subscription {
    var subscriber: Subscriber[_ >: T] = null
    var requests: List[Long]           = Nil

    override def subscribe(s: Subscriber[_ >: T]): Unit = {
      subscriber = s
      s.onSubscribe(this)
    }

    override def cancel(): Unit = ???

    override def request(n: Long): Unit = {
      requests = n :: requests
    }
  }

}
