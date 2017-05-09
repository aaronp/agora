package jabroni.rest.worker

import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpRequest, StatusCodes}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import io.circe.generic.auto._
import jabroni.api._
import jabroni.api.`match`.MatchDetails
import jabroni.api.exchange.{Exchange, MatchObserver, WorkSubscription}
import jabroni.rest.BaseSpec
import jabroni.rest.multipart.MultipartBuilder

import language.reflectiveCalls

class WorkerRoutesTest extends BaseSpec {

  import WorkerRoutesTest._

  "WorkerRoutes" should {
    "handle dynamically created routes" in {

      val bytes = ByteString("Testing 123")
      val request = MultipartBuilder().
        json(SomeData("hello", 654), "first").
        json(SomeData("more", 111), "second").
        json(SomeData("again", 8), "third").
        file("key1", "foo.csv", HttpEntity(ContentTypes.`text/plain(UTF-8)`, "2,3,5\n4,5,6")).
        source("bar.csv", bytes.size, Source.single(bytes)).multipart


      val details = MatchDetails(nextMatchId(), nextSubscriptionKey(), nextJobId(), 3, 1234567)

      val httpReq: HttpRequest = WorkerClient.multipartRequest("doit", details, request)

      val wr = WorkerRoutes(Exchange(MatchObserver()), WorkSubscription(), 1)

      // we should get an error as we don't know how to handle 'doit' yet
      //      httpReq ~> wr.routes ~> check {
      //        //        val x = response
      //        //        println(x.status)
      //        rejection
      //      }

      // add a handler for 'doit'
      wr.handleMultipart { ctxt =>

        val first = ctxt.textPart("first").block
        first shouldBe """{"foo":"hello","bar":654}"""
        ctxt.textPart("second").block shouldBe """{"foo":"more","bar":111}"""
        ctxt.textPart("third").block shouldBe """{"foo":"again","bar":8}"""
        ctxt.textPart("key1").block shouldBe "2,3,5\n4,5,6"

        val files = ctxt.multipartKeys.flatMap(_.fileName)
        files should contain only ("foo.csv")

        first
      }(wr.defaultSubscription.withPath("doit"))

      httpReq ~> wr.routes ~> check {
        status shouldEqual StatusCodes.OK
      }

    }
  }

}

object WorkerRoutesTest {

  case class SomeData(foo: String, bar: Int)


  //  val multipartUnmarshallers : Unmarshaller[HttpRequest, MultipartPieces] = Unmarshaller.multipartUnmarshaller()

}