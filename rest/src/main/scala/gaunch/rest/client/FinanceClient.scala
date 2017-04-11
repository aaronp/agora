package jabroni.rest.client

import scala.language.reflectiveCalls

import java.io.Closeable

import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
import akka.http.scaladsl.unmarshalling.{Unmarshal, Unmarshaller}
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import com.typesafe.scalalogging.StrictLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import jabroni.api.{Ledger, Order, OrderBook}
import jabroni.json.JsonSupport

import scala.concurrent.Future
import scala.scalajs.niocharset.StandardCharsets

/**
  * Represents a client to the ledger.
  *
  * At the moment there's no real reason to extend the Ledger, other than having a bit of readability
  * between the client interface (trait) and the companion object used to created remote instances.
  *
  * e.g. this:
  * {{[
  * val client : FinanceClient = ...
  * }}}
  *
  * or indeed
  * {{[
  * val client =  FinanceClient(...) // returns a FinanceClient rather than a Ledger
  * }}}
  *
  * is less surprising than:
  * {{{
  *   val client : Ledger = FinanceClient(...)
  * }}}
  *
  * Perhaps more-so too is that referenced to 'FinanceClient' are more independent of
  * the Ledger, should we need to make that separation.
  */
trait FinanceClient extends Ledger


object FinanceClient {

  def apply(config: ClientConfig): FinanceClient = new RemoteClient(config)

  private class RemoteClient(config: ClientConfig) extends FinanceClient
    with JsonSupport
    with FailFastCirceSupport
    with StrictLogging
    with Closeable {

    private val endpoint: RestClient = RestClient(config)

    import config.implicits._
    import endpoint._

    private def mkUri(path : String) = Uri(s"http://${config.host}:${config.port}/rest/$path")

    override def placeOrder(order: Order): Future[Boolean] = {
      val buyOrSell = order.`type`.toString.toLowerCase
      val json = order.toJsonString
      val body = HttpEntity(`application/json`, json)
      val request = HttpRequest(PUT, mkUri(buyOrSell), entity = body)
      send(request).map { r =>
        r.status.isSuccess()
      }
    }

    override def cancelOrder(order: Order): Future[Boolean] = {
      val body = HttpEntity(`application/json`, order.toJsonString)
      val uri = mkUri("cancel")
      val resp: Future[HttpResponse] = send(HttpRequest(DELETE, uri, entity = body))
      // TODO - this is a bit naughty - just parsing the body as 'true' / 'false' text.
      // it'd be good to use an appropriate return code on it's own -- need to check the internets for how to
      // do that 'properly'
      val boolBodyFuture = resp.flatMap(_.entity.dataBytes.runWith(Sink.head)).map(_.decodeString("UTF8").toBoolean)
      boolBodyFuture.flatMap { jsonBodyOk =>
        resp.map(_.status.isSuccess() && jsonBodyOk)
      }

    }

    override def orderBook: Future[OrderBook] = {

      val req = HttpRequest(GET, mkUri("orders"))

      implicit val un: Unmarshaller[HttpResponse, OrderBook] = Unmarshaller { ec =>
        resp: HttpResponse =>
          val bytes: Source[ByteString, Any] = resp.entity.dataBytes

          val all: Future[ByteString] = bytes.runWith(Sink.reduce(_ ++ _))

          all.map { bytes =>
            val Right(book) = bytes.decodeString(StandardCharsets.UTF_8).asOrderBook
            book
          }
      }
      send(req).flatMap { resp =>
        val im = implicitly[Materializer]
        Unmarshal(resp).to[OrderBook](um = un, ec = null, mat = im)
      }
    }

    override def close(): Unit = endpoint.close()
  }

}
