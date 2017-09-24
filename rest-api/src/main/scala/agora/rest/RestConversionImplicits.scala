package agora.rest

import agora.api.exchange.{Dispatch, AsClient}
import agora.io.IterableSubscriber
import agora.rest.worker.WorkerClient
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.unmarshalling.{FromResponseUnmarshaller, Unmarshal}
import akka.stream.Materializer

import scala.concurrent.Future

/**
  * Contains implicits for creating [[AsClient]] instances
  */
trait RestConversionImplicits {
  import RestConversionImplicits._

  implicit def asWorkerClientDispatchable[T](implicit conf: ClientConfig, ev: ToEntityMarshaller[T]): AsClient[T, HttpResponse] = {
    asRichClientConfig(conf)
  }

  def asRichClientConfig[T: ToEntityMarshaller](conf: ClientConfig): AsClient[T, HttpResponse] = {
    new ClientConfigOps(conf).asDispatchable[T]
  }

  implicit def asRichDispatchable[A](asClient: AsClient[A, HttpResponse]) = new AsClientOps(asClient)
}

object RestConversionImplicits {

  class ClientConfigOps(conf: ClientConfig) {

    def asDispatchable[T: ToEntityMarshaller]: AsClient[T, HttpResponse] = {
      new AsClient[T, HttpResponse] {
        override def dispatch(dispatch: Dispatch[T]): Future[HttpResponse] = {
          WorkerClient(conf, dispatch).sendRequest(dispatch.request)
        }
      }
    }
  }

  class AsClientOps[A](asClient: AsClient[A, HttpResponse]) {

    /**
      * If we have a [[FromResponseUnmarshaller]] for T, then we can turn a asClient from
      * AsClient[A, HttpResponse] to  AsClient[A, T]
      *
      * @param mat a materializer for unmarshalling
      * @tparam T the unmarshalled type
      * @return a asClient from
      *         AsClient[A, HttpResponse] to  AsClient[A, T]
      */
    def unmarshalledTo[T: FromResponseUnmarshaller](implicit mat: Materializer): AsClient[A, T] = {
      import mat.executionContext
      asClient.flatMap { resp =>
        Unmarshal(resp).to[T]
      }
    }

    def iterate(maximumFrameLength: Int = 1000, allowTruncation: Boolean = true)(implicit mat: Materializer): AsClient[A, Iterator[String]] = {
      import mat.executionContext
      asClient.map { resp =>
        IterableSubscriber.iterate(resp.entity.dataBytes, maximumFrameLength, allowTruncation)
      }
    }
  }

}
