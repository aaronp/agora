package agora.rest
package worker

import agora.api._
import agora.api.`match`.MatchDetails
import agora.api.exchange.WorkSubscription
import agora.domain.IterableSubscriber
import agora.health.HealthDto
import agora.io.Sources
import agora.rest.multipart.{MultipartBuilder, MultipartInfo}
import akka.http.scaladsl.model._
import akka.stream.scaladsl.{Sink, Source}
import akka.util.ByteString
import io.circe.generic.auto._
import io.circe.optics.JsonPath
import io.circe.syntax._

import scala.concurrent.Future

class WorkerRoutesTest extends BaseRoutesSpec {

  import WorkerRoutesTest._

  "WorkerRoutes.health" should {
    "reply w/ the worker health" in {

      WorkerHttp.healthRequest ~> WorkerRoutes().routes ~> check {
        status shouldEqual StatusCodes.OK
        val dto = responseAs[HealthDto]
        dto.objectPendingFinalizationCount should be >= 0
        contentType shouldBe ContentTypes.`application/json`
      }
    }
  }
  "WorkerRoutes.updateSubscription" should {
    "replace the subscription details" in {
      // start out with a single route, but whos handler replaces (updates) the subscription
      val wr = WorkerRoutes()

      def invocations(ctxt: WorkContext[_]) = JsonPath.root.invoked.int.getOption(ctxt.details.aboutMe).getOrElse(0)

      // start off with an initial subscription containing some details...
      wr.usingSubscription(_.withPath("original").withSubscriptionKey("firstKey").append("someValue", "hello")).addHandler[Int] { ctxt =>
        // now within the handler, update the details ... inc a counter and set a new path
        val b4 = invocations(ctxt)

        val calls   = b4 + 1
        val ctxtFut = ctxt.updateSubscription(_.withPath("updated").append("invoked", calls))

        ctxtFut.foreach { newCtxt =>
          newCtxt.request(1)
        }

        val nrToRequest = 1 // b4.min(1)
        ctxt.completeWith(ctxt.asResponse(calls), nrToRequest)
      }

      // verify our initial subscription
      val initialState               = wr.exchange.queueState().futureValue
      val List(originalSubscription) = initialState.subscriptions
      originalSubscription.requested shouldBe 1

      val originalData = originalSubscription.subscription.details.aboutMe
      val someValue    = JsonPath.root.someValue.string.getOption(originalData)
      someValue shouldBe Option("hello")
      JsonPath.root.invoked.int.getOption(originalData) shouldBe None

      originalSubscription.subscription.details.path shouldBe "original"
      originalSubscription.subscription.key shouldBe Option("firstKey")

      // call the method under test -- invoke our handler and observer that the exchange sees an updated subscription
      def httpRequest(path: String) = WorkerClient.dispatchRequest(path, matchDetails, 123)

      /**
        * assert the
        *
        * @param expectedCalls
        * @return
        */
      def verifyUpdatedSubscription(expectedCalls: Int): WorkSubscription = {
        // we should still only have one subscription
        val newState              = wr.exchange.queueState().futureValue
        val List(newSubscription) = newState.subscriptions

        // verify new subscription
        val newData = newSubscription.subscription.details.aboutMe
        JsonPath.root.someValue.string.getOption(newData) shouldBe Option("hello")
        JsonPath.root.invoked.int.getOption(newData) shouldBe Option(expectedCalls)

        newSubscription.subscription.details.path shouldBe "updated"
        newSubscription.subscription.key shouldBe Option("firstKey")
        newSubscription.subscription
      }

      val a = httpRequest("original")
      println(a)
      a ~> wr.routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[Int] shouldBe 1
        verifyUpdatedSubscription(1)
      }

      // the 'original' handler should now be a 404, as our updated subscription changed the path from 'original'
      // to 'updated'
      httpRequest("original") ~> wr.routes ~> check {
        status shouldEqual StatusCodes.NotFound
      }

      // invoke our handler, which should then update the subscription held on the exchange
      (2 to 5).foreach { expectedInvocation =>
        httpRequest("updated") ~> wr.routes ~> check {
          status shouldEqual StatusCodes.OK
          responseAs[Int] shouldBe expectedInvocation
          verifyUpdatedSubscription(expectedInvocation)
        }
      }
    }
  }
  "WorkerRoutes.become" should {
    "replace existing routes" in {
      val wr = WorkerRoutes()

      var oldCount = 0
      var newCount = 0

      def newHandler(ctxt: WorkContext[String]) = {
        newCount = newCount + 1
        ctxt.complete("second handler response")
      }

      wr.usingSubscription(_.withPath("somePath")).addHandler[String] { ctxt =>
        oldCount = oldCount + 1
        ctxt.become(newHandler)
        ctxt.complete("first handler response")
      }

      val httpRequest = WorkerClient.dispatchRequest("somePath", matchDetails, "foo")

      // make the first request
      httpRequest ~> wr.routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] shouldBe "first handler response"
        oldCount shouldBe 1
        newCount shouldBe 0
      }

      // make a second request, which should go to the new handler
      httpRequest ~> wr.routes ~> check {
        status shouldEqual StatusCodes.OK
        responseAs[String] shouldBe "second handler response"
        oldCount shouldBe 1
        newCount shouldBe 1
      }
    }
  }
  "WorkerRoutes" should {

    "be able to upload from strict multipart requests" in {
      val wr = WorkerRoutes()

      wr.usingSubscription(_.withPath("uploadTest")).addHandler[Multipart.FormData] { workContext =>
        val respFuture: Future[HttpResponse] = workContext.mapFirstMultipart {
          case (MultipartInfo(_, Some("some.file"), _), upload) =>
            val subscriber = new IterableSubscriber[ByteString]()
            upload.runWith(Sink.fromSubscriber(subscriber))
            val lines             = subscriber.iterator.map(_.utf8String).toList
            val e: ResponseEntity = lines.mkString("\n")
            HttpResponse(entity = e.withContentType(ContentTypes.`text/plain(UTF-8)`))
        }

        workContext.completeWith(respFuture)
      }

      val expectedContent = "It was the best of times\nIt was the worst of times\nAs I write this, Trump just fired James Comey\n"
      val upload = {
        Multipart.FormData(Multipart.FormData.BodyPart.Strict("csv", HttpEntity(ContentTypes.`text/plain(UTF-8)`, expectedContent), Map("filename" -> "some.file")))
      }

      val httpRequest = WorkerClient.multipartRequest("uploadTest", matchDetails, upload)

      httpRequest ~> wr.routes ~> check {
        status shouldEqual StatusCodes.OK
        val text = response.entity.toStrict(testTimeout).block.data.utf8String
        contentType shouldBe ContentTypes.`text/plain(UTF-8)`
        text shouldBe expectedContent
      }
    }

    "be able to upload from indefinite multipart requests" in {
      val wr = WorkerRoutes()

      wr.usingSubscription(_.withPath("uploadTest")).addHandler { (ctxt: WorkContext[Multipart.FormData]) =>
        ctxt.foreachMultipart {
          case (MultipartInfo("some.file", _, _), upload) =>
            val lines             = IterableSubscriber.iterate(upload, 100, true)
            val e: ResponseEntity = lines.mkString("\n")
            ctxt.complete {
              HttpResponse(entity = e.withContentType(ContentTypes.`text/plain(UTF-8)`))
            }
        }
      }

      val expectedContent =
        """here
          |is
          |a
          |bunch of
          |content
        """.stripMargin

      def upload = {
        val bytes = Sources.asBytes(Source.single(expectedContent), "")
        val len   = Sources.sizeOf(bytes).futureValue

        MultipartBuilder().fromSource("some.file", len, bytes, fileName = "some.file").formData.futureValue
      }

      val httpRequest = WorkerClient.multipartRequest("uploadTest", matchDetails, upload)

      httpRequest ~> wr.routes ~> check {
        status shouldEqual StatusCodes.OK
        val text = response.entity.toStrict(testTimeout).block.data.utf8String
        contentType shouldBe ContentTypes.`text/plain(UTF-8)`
        text shouldBe expectedContent
      }
    }
    "handle dynamically created routes" in {

      val request = MultipartBuilder()
        .json("first", SomeData("hello", 654))
        .json("second", SomeData("more", 111))
        .json("third", SomeData("again", 8))
        .text("key1", "2,3,5\n4,5,6")
        .formData
        .futureValue

      val httpReq: HttpRequest = WorkerClient.multipartRequest("doit", matchDetails, request)

      val wr = WorkerRoutes()

      // add a handler for 'doit'
      wr.usingSubscription(_.withPath("doit")).addHandler[Multipart.FormData] { ctxt =>
        val textByKeyFuture = ctxt.flatMapMultipart {
          case (MultipartInfo(key, _, _), src) => Sources.asText(src).map(text => (key, text))
        }

        textByKeyFuture.foreach { pears =>
          ctxt.completeWithJson {
            pears.toMap.asJson
          }
        }
      }

      httpReq ~> wr.routes ~> check {
        status shouldEqual StatusCodes.OK
        val textByKey = responseAs[Map[String, String]]
        textByKey shouldBe Map(
          "first"  -> "{\"foo\":\"hello\",\"bar\":654}",
          "second" -> "{\"foo\":\"more\",\"bar\":111}",
          "third"  -> "{\"foo\":\"again\",\"bar\":8}",
          "key1"   -> "2,3,5\n4,5,6"
        )
      }
    }

    "handle routes with paths of varying length" in {
      val wr = WorkerRoutes()

      // add an echo handler for 'a/b/c'
      wr.usingSubscription(_.withPath("some/nested/route/echo")).addHandler[String] { ctxt =>
        ctxt.complete {
          s"got ${ctxt.request}"
        }
      }

      Post("/some/nested/route/echo", "hello world") ~> wr.routes ~> check {
        status shouldEqual StatusCodes.OK
        val echo = responseAs[String]
        echo shouldBe "got hello world"
      }
    }
  }
}

object WorkerRoutesTest {

  case class SomeData(foo: String, bar: Int)

  val matchDetails = MatchDetails(nextMatchId(), nextSubscriptionKey(), nextJobId(), 3, 1234567)

}
