package streaming.vertx.client

import agora.io.ToBytes
import io.vertx.core.buffer.Buffer
import io.vertx.lang.scala.ScalaVerticle
import io.vertx.scala.core.Vertx
import io.vertx.scala.core.http.{HttpClient, HttpClientRequest}
import monix.execution.{CancelableFuture, Scheduler}
import monix.reactive.{Observable, Observer, Pipe}
import streaming.rest.{EndpointCoords, HttpMethod, WebURI}
import streaming.vertx.client.RestClient.RestInput

case class RestClient(val coords: EndpointCoords,
                      val requests: Observer[RestInput],
                 observable: Observable[RestInput],
                 impl: Vertx = Vertx.vertx())(implicit scheduler : Scheduler) extends ScalaVerticle {
  vertx = impl
  val httpClient: HttpClient = vertx.createHttpClient
  val sendingFuture: CancelableFuture[Unit] = observable.foreach(r => send(r))

  start()

  def send[A](req: RestInput): Either[String, HttpClientRequest] = {
    req.uri.resolve(req.headers).right.map { parts =>
      val uri = parts.mkString("/")

      val httpRequest: HttpClientRequest = req.uri.method match {
        case HttpMethod.GET => httpClient.get(coords.port, coords.host, uri)
        case HttpMethod.POST => httpClient.post(coords.port, coords.host, uri)
        case HttpMethod.PUT => httpClient.put(coords.port, coords.host, uri)
        case HttpMethod.DELETE => httpClient.delete(coords.port, coords.host, uri)
        case HttpMethod.HEAD => httpClient.head(coords.port, coords.host, uri)
        case HttpMethod.OPTIONS => httpClient.options(coords.port, coords.host, uri)
      }

      httpRequest.write(Buffer.buffer(req.bodyAsBytes))

      httpRequest
    }
  }
}

object RestClient {

  sealed trait RestInput {
    def uri: WebURI
    def headers: Map[String, String]
    def bodyAsBytes: Array[Byte] = this match {
      case content : RestInput.ContentInput[_] => content.bytes
      case _ => Array.empty[Byte]
    }
  }
  object RestInput {
    case class BasicInput(uri: WebURI, headers: Map[String, String]) extends RestInput
    case class ContentInput[A : ToBytes](uri: WebURI, headers: Map[String, String], body: A) extends RestInput {
      def bytes: Array[Byte] = ToBytes[A].bytes(body)
    }
  }

  def connect(coords: EndpointCoords)(implicit scheduler : Scheduler): RestClient = {
    val (requests: Observer[RestInput], observer: Observable[RestInput]) = Pipe.publish[RestInput].unicast
    RestClient(coords, requests, observer)
  }
}
