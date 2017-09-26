package agora.rest

import agora.BaseSpec
import agora.api.exchange.{AsClient, SubmitJob}
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import io.circe.Json

class RestConversionImplicitsTest extends BaseSpec with RestConversionImplicits with HasMaterializer {

  import RestConversionImplicitsTest._

  "RestConversionImplicits" should {
    "be able to produce an AsClient[HttpRequest, HttpResponse] given an implicit workerConfig is in scope" in {
      implicit val conf: ClientConfig                   = ClientConfig.load()
      val asClient: AsClient[HttpRequest, HttpResponse] = implicitly[AsClient[HttpRequest, HttpResponse]]
      asClient should not be null
    }
    "be able to produce an AsClient[T, HttpResponse] given an implicit ToEntityMarshaller and client config in scope" in {
      import io.circe.generic.auto._
      implicit val conf: ClientConfig                     = ClientConfig.load()
      val asClient: AsClient[SomeCustomDto, HttpResponse] = AsClient.instance[SomeCustomDto, HttpResponse]
      asClient should not be null
    }
    "be able to produce an AsClient[A, B] given an implicit ToEntityMarshaller, FromEntityUnmarshaller and client config in scope" in {
      import io.circe.generic.auto._
      implicit val conf: ClientConfig                        = ClientConfig.load()
      val asClient: AsClient[SomeCustomDto, SomeResponseDto] = AsClient.instance[SomeCustomDto, SomeResponseDto]
      asClient should not be null
    }
    "be able to produce an AsClient[SubmitJob, B] given an implicit FromEntityUnmarshaller and client config in scope" in {
      import io.circe.generic.auto._
      implicit val conf: ClientConfig                    = ClientConfig.load()
      val asClient: AsClient[SubmitJob, SomeResponseDto] = AsClient.instance[SubmitJob, SomeResponseDto]
      asClient should not be null
    }
    "be able to produce an AsClient[String, Json] given an implicit FromEntityUnmarshaller and client config in scope" in {
      import io.circe.generic.auto._
      implicit val conf: ClientConfig      = ClientConfig.load()
      val asClient: AsClient[String, Json] = AsClient.instance[String, Json]
      asClient should not be null
    }
  }
}

object RestConversionImplicitsTest {

  case class SomeCustomDto(hi: String, there: Int)

  case class SomeResponseDto(meh: Double)

}
