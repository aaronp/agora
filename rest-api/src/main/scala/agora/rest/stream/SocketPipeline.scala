package agora.rest.stream

import agora.flow.{HasName, _}
import agora.rest.exchange.ClientSubscriptionMessage
import akka.NotUsed
import akka.http.scaladsl.model.ws.{Message, TextMessage}
import akka.stream.scaladsl.{Flow, Sink, Source}
import com.typesafe.scalalogging.StrictLogging
import io.circe.parser.parse
import io.circe.syntax._
import io.circe.{Decoder, Encoder}
import org.reactivestreams.{Publisher, Subscriber, Subscription}

import scala.concurrent.ExecutionContext

object SocketPipeline extends StrictLogging {

  object DataSubscriber {
    def apply[FromRemote: Decoder](name: String, dao: DurableProcessorDao[FromRemote])(implicit ec: ExecutionContext) = {
      new NamedDataSubscriber[FromRemote](name, dao)
    }

    def apply[FromRemote: Decoder](dao: DurableProcessorDao[FromRemote])(implicit ec: ExecutionContext) = {
      new DataSubscriber[FromRemote](dao)
    }

    def apply[FromRemote: Decoder]()(implicit ec: ExecutionContext): DataSubscriber[FromRemote] = {
      apply[FromRemote](DurableProcessorDao[FromRemote]())
    }
  }

  class NamedDataSubscriber[FromRemote: Decoder](override val name: String, dao: DurableProcessorDao[FromRemote])(implicit ec: ExecutionContext)
      extends DataSubscriber[FromRemote](dao)

  /**
    * Publishes ClientSubscriptionMessage and consumes Json (decoded into FromRemote]
    */
  class DataSubscriber[FromRemote: Decoder](dao: DurableProcessorDao[FromRemote])(implicit ec: ExecutionContext) extends HasName {

    def takeNext(n: Int) = controlMessagePublisher.onNext(TakeNext(n))

    def cancel() = controlMessagePublisher.onNext(Cancel)

    // send control messages up to remote. akka io can request/pull as fast as it likes
    private[stream] val controlMessagePublisher = DurableProcessor[ClientSubscriptionMessage]()

    /**
      * The republishingDataConsumer listens to incoming data, and so can be subscribed to observe the incoming data.
      *
      * It won't request any data itself, but will consume data as quickly as its fastest subscriber...
      *
      * AND SO NEEDS SOMEBODY LISTENING/CONSUMING FOR IT TO PULL ANY DATA.
      *
      * As it is a durable processor, that data is written down, so the consumer may simply be a no-op or logging
      * listener of some sort.
      *
      */
    val republishingDataConsumer = new DurableProcessor.Instance[FromRemote](dao) {
      override def onNext(value: FromRemote): Unit = {
        logger.debug(s"Republishing $name\n${snapshot()} >>>>\n$value\n<<<<\n")
        super.onNext(value)
      }
      override protected def onRequest(n: Long) = {
        controlMessagePublisher.onNext(TakeNext(n))
      }

      override def cancel() = {
        super.cancel()
        controlMessagePublisher.onNext(Cancel)
      }
    }

    def snapshot() = republishingDataConsumer.snapshot()

    override def name: String = {
      val underlyingName = republishingDataConsumer.snapshot().toString
      s"DataSubscriber[$hashCode] for $underlyingName"
    }

    /**
      * This flow can be used to connect to a websocket
      */
    lazy val flow: Flow[Message, Message, NotUsed] = {
      val flowMessageSource: Source[Message, NotUsed] = Source.fromPublisher(controlMessagePublisher).map { value =>
        TextMessage(value.asJson.noSpaces)
      }

      val kitchen: Sink[Message, NotUsed] = Sink.fromSubscriber(republishingDataConsumer).contramap(unmarshal[FromRemote](name, republishingDataConsumer))
      Flow.fromSinkAndSource(kitchen, flowMessageSource)
    }
  }

  object DataPublisher {
    def apply[ToRemote: Encoder](name: String, publisher: Publisher[ToRemote])(implicit ec: ExecutionContext) = {
      new NamedDataPublisher[ToRemote](name, publisher)
    }

    def apply[ToRemote: Encoder](publisher: Publisher[ToRemote])(implicit ec: ExecutionContext) = {
      new DataPublisher[ToRemote](publisher)
    }
  }

  class NamedDataPublisher[ToRemote: Encoder](override val name: String, publisher: Publisher[ToRemote])(implicit ec: ExecutionContext)
      extends DataPublisher(publisher)

  /**
    * Publishes json messages and consumes ClientSubscriptionMessage
    *
    * Wires in the supplied localPublisher with remote subscriptions which will sent 'take next' and 'cancel' control messages
    *
    * localPublisher --- buffer --->  flowProcessor --> .... socket
    *
    * the socket will initially pull (e.g. 16) from the flowProcessor, which pulls from the buffer.
    *
    * There won't be any data in the buffer though as it won't pull from the localPublisher until an explicit
    * request comes in via the controlMessageProcessor
    */
  class DataPublisher[ToRemote: Encoder](val localPublisher: Publisher[ToRemote])(implicit ec: ExecutionContext) extends HasName {

    def snapshot(): Option[PublisherSnapshot[Int]] = {
      localPublisher match {
        case s: PublisherSnapshotSupport[Int] => Option(s.snapshot())
        case _                                => None
      }
    }

    def bufferSnapshot() = buffer.snapshot()

    def flowSnapshot() = flowProcessor.snapshot()

    /**
      * the buffer feeds the processor connected to the flow. The flow will immediately request e.g. 16 elements,
      * but the buffer will only pull from the localPublisher (and thus republish to the flowProcessor) when
      * explicitly asked to from the [[ClientSubscriptionMessage]]s we're receiving from the remote server
      */
    private[stream] val (buffersSubscriptionToLocalProcessor: Subscription, buffer: DurableProcessor.Instance[ToRemote]) = {
      val dao = DurableProcessorDao[ToRemote](20)

      // don't automatically pull from the local producer, but rather only when explicitly requested
      val p = new DurableProcessor.Instance(dao, propagateSubscriberRequestsToOurSubscription = false) with HasName {
        override def name = s"buffer w/ ${snapshot()}"
      }

      // when the local producer gets messages, we push to our buffer
      localPublisher.subscribe(p)

      val buffersSubscriptionToTheLocalProcessor: Subscription = p.processorSubscription().get

      buffersSubscriptionToTheLocalProcessor -> p
    }

    /**
      * This will initially pull based on akka io logic (e.g. request 16) and be fed from
      * a buffer used to throttle the requests.
      */
    private[stream] val flowProcessor = {
      val p = DurableProcessor[ToRemote]()
      buffer.subscribe(p)
      p
    }

    // listen to control messages coming from remote
    private[stream] val controlMessageProcessor = {

      val p = DurableProcessor[ClientSubscriptionMessage]()

      val actionFromControl: BaseSubscriber[ClientSubscriptionMessage] = BaseSubscriber(10) {
        case (sub, Cancel) =>
          buffersSubscriptionToLocalProcessor.cancel
          sub.cancel()
        case (sub, TakeNext(n)) =>
          buffersSubscriptionToLocalProcessor.request(n)
          sub.request(1)
      }
      p.subscribe(actionFromControl)
      p
    }

    def cancel(): ClientSubscriptionMessage = {
      val msg = Cancel
      controlMessageProcessor.onNext(msg)
      msg
    }

    // forces a take next
    def takeNext(n: Int): ClientSubscriptionMessage = {
      val msg = TakeNext(n)
      controlMessageProcessor.onNext(msg)
      msg
    }

    lazy val flow: Flow[Message, Message, NotUsed] = {

      val flowMessageSource: Source[Message, NotUsed] = Source.fromPublisher(flowProcessor).map { value =>
        TextMessage(value.asJson.noSpaces)
      }

      /*
       * These are the messages we'll receive from the server -- and as WE'RE publishing data to it, the server
       * will be sending 'TakeNext' or 'Cancel' messages.
       */
      val kitchen: Sink[Message, NotUsed] = Sink.fromSubscriber(controlMessageProcessor).contramap(unmarshal[ClientSubscriptionMessage](name, buffer))

      Flow.fromSinkAndSource(kitchen, flowMessageSource)
    }

    override def name: String = {
      val underlyingName = localPublisher match {
        case hn: HasName => hn.name
        case other       => other.toString
      }
      s"DataPublisher[$hashCode] for $underlyingName"
    }
  }

  private def unmarshal[T: Decoder](name: String, buffer: Subscriber[_])(msg: Message): T = {
    msg match {
      case TextMessage.Strict(jsonText) =>
        logger.debug(s"\t$name received: $jsonText")
        parse(jsonText) match {
          case Left(err) =>
            val wrapped = new Exception(s"$name couldn't parse ${jsonText} : $err")
            buffer.onError(wrapped)
            throw wrapped
          case Right(json) =>
            json.as[T] match {
              case Right(msg) => msg
              case Left(err) =>
                buffer.onError(err)
                throw err
            }
        }
      case other =>
        val err = new Exception(s"$name: Expected a strict message but got " + other)
        buffer.onError(err)
        throw err
    }
  }

}
