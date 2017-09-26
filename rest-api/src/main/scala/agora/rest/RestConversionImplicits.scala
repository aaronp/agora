package agora.rest

import agora.api.exchange.{AsClient, Dispatch}
import agora.io.IterableSubscriber
import agora.rest.worker.WorkerClient
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, FromResponseUnmarshaller, Unmarshal}
import akka.stream.Materializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport

import scala.concurrent.Future

/**
  * Contains implicits for creating [[AsClient]] instances
  */
trait RestConversionImplicits extends FailFastCirceSupport {

  import RestConversionImplicits._

  implicit def asWorkerClient(implicit conf: ClientConfig): AsClient[HttpRequest, HttpResponse] = {
    ClientConfigOps(conf).asClient
  }

  implicit def asMarshalledClient[T: ToEntityMarshaller](implicit conf: ClientConfig): AsClient[T, HttpResponse] = {
    ClientConfigOps(conf).asClientForEntity[T]
  }

  implicit def asInferredClient[A: ToEntityMarshaller, B: FromEntityUnmarshaller](
      implicit conf: ClientConfig,
      mat: Materializer): AsClient[A, B] = {
    ClientConfigOps(conf).asInferredClient[A, B]
  }

  implicit def asRichAsClientHttpResponse[A](implicit asClient: AsClient[A, HttpResponse]): AsClientOps[A] = {
    new AsClientOps[A](asClient)
  }
}

object RestConversionImplicits {

  case class ClientConfigOps(conf: ClientConfig) {

    def asClient: AsClient[HttpRequest, HttpResponse] = {
      new AsClient[HttpRequest, HttpResponse] {
        override def dispatch(dispatch: Dispatch[HttpRequest]): Future[HttpResponse] = {
          WorkerClient(conf, dispatch).send(dispatch.request)
        }
      }
    }

    def asClientForEntity[T: ToEntityMarshaller]: AsClient[T, HttpResponse] = {
      new AsClient[T, HttpResponse] {
        override def dispatch(dispatch: Dispatch[T]): Future[HttpResponse] = {
          WorkerClient(conf, dispatch).sendRequest(dispatch.request)
        }
      }
    }
    def asInferredClient[A: ToEntityMarshaller, B: FromResponseUnmarshaller](
        implicit mat: Materializer): AsClient[A, B] = {
      import mat._
      import akka.http.scaladsl.util.FastFuture._
      new AsClient[A, B] {
        override def dispatch(dispatch: Dispatch[A]): Future[B] = {
          val future = WorkerClient(conf, dispatch).sendRequest(dispatch.request)
          future.fast.flatMap { resp =>
            Unmarshal(resp).to[B]
          }
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

    def iterate(maximumFrameLength: Int = 1000, allowTruncation: Boolean = true)(
        implicit mat: Materializer): AsClient[A, Iterator[String]] = {
      import mat.executionContext
      asClient.map { resp =>
        IterableSubscriber.iterate(resp.entity.dataBytes, maximumFrameLength, allowTruncation)
      }
    }
  }

}
