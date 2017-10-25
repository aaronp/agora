package agora.rest

import agora.api.exchange.{AsClient, Dispatch}
import agora.io.IterableSubscriber
import agora.rest.worker.WorkerClient
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, FromResponseUnmarshaller, Unmarshal}
import akka.stream.Materializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.Json

import scala.concurrent.Future

/**
  * Contains implicits for creating [[AsClient]] instances
  *
  * In an implicit [[ClientConfig]] is in scope, then we can know how to connect to workers from [[Dispatch]] responses
  * and so can provide an [[AsClient]] instance.
  *
  * Furthermore, if the right marshallers/unmarshallers are in scope, then we can further refine the requests/responses
  * to more specific types
  */
trait RestConversionImplicits extends FailFastCirceSupport {

  import RestConversionImplicits._

  implicit def asConfigOps(implicit conf: ClientConfig): ClientConfigOps = new ClientConfigOps(conf)

  /**
    * In an implicit [[ClientConfig]] is in scope, then we can know how to connect to workers from [[Dispatch]] responses
    * and so can provide an [[AsClient]] instance
    *
    * @param conf the client config
    * @return an AsClient for generic http requests/responses
    */
  implicit def asWorkerClient(implicit conf: ClientConfig): AsClient[HttpRequest, HttpResponse] = {
    asConfigOps(conf).httpClient
  }

  implicit def asMarshalledClient[T: ToEntityMarshaller](implicit conf: ClientConfig): AsClient[T, HttpResponse] = {
    asConfigOps(conf).forEntity[T]
  }

  implicit def asInferredClient[A, B](implicit conf: ClientConfig,
                                      mat: Materializer,
                                      tem: ToEntityMarshaller[A],
                                      fem: FromEntityUnmarshaller[B]): AsClient[A, B] = {
    asConfigOps(conf).typed[A, B]
  }

  implicit def asRichAsClientHttpResponse[A](implicit asClient: AsClient[A, HttpResponse]): AsClientOps[A] = {
    new AsClientOps[A](asClient)
  }

  implicit class RichHttpClient[A](asClient: AsClient[A, HttpResponse]) {
    def returning[B: FromResponseUnmarshaller](implicit mat: Materializer): AsClient[A, B] = {
      import akka.http.scaladsl.util.FastFuture._
      import mat._
      new AsClient[A, B] {
        override def dispatch[A1 <: A](dispatch: Dispatch[A1]): Future[B] = {
          asClient.dispatch(dispatch).fast.flatMap { resp =>
            Unmarshal(resp).to[B]
          }
        }
      }
    }
  }

}

object RestConversionImplicits {

  object implicits extends RestConversionImplicits

  class ClientConfigOps(val conf: ClientConfig) extends AnyVal {

    def httpClient: AsClient[HttpRequest, HttpResponse] = {
      new AsClient[HttpRequest, HttpResponse] {
        override def dispatch[T <: HttpRequest](dispatch: Dispatch[T]): Future[HttpResponse] = {
          WorkerClient(conf, dispatch).send(dispatch.request)
        }
      }
    }

    def forEntity[T: ToEntityMarshaller]: AsClient[T, HttpResponse] = {
      new AsClient[T, HttpResponse] {
        override def dispatch[T1 <: T](dispatch: Dispatch[T1]): Future[HttpResponse] = {
          WorkerClient(conf, dispatch).sendRequest(dispatch.request)
        }
      }
    }
    def returning[B: FromResponseUnmarshaller](implicit mat: Materializer): AsClient[HttpRequest, B] = {
      import implicits._
      httpClient.returning[B]
    }

    def typed[A: ToEntityMarshaller, B: FromResponseUnmarshaller](implicit mat: Materializer): AsClient[A, B] = {
      import akka.http.scaladsl.util.FastFuture._
      import mat._
      new AsClient[A, B] {
        override def dispatch[A1 <: A](dispatch: Dispatch[A1]) = {
          WorkerClient(conf, dispatch).sendRequest(dispatch.request).fast.flatMap { resp =>
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

    def iterate(maximumFrameLength: Int = 1000, allowTruncation: Boolean = true)(implicit mat: Materializer): AsClient[A, Iterator[String]] = {
      import mat.executionContext
      asClient.map { resp =>
        IterableSubscriber.iterate(resp.entity.dataBytes, maximumFrameLength, allowTruncation)
      }
    }
  }

}
