package agora.rest

import agora.BaseSpec
import agora.api.exchange.{AsClient, SubmitJob}
import agora.rest.RestConversionImplicits.ClientConfigOps
import akka.http.scaladsl.marshalling.ToEntityMarshaller
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.unmarshalling.FromEntityUnmarshaller
import akka.stream.Materializer
import io.circe.Json

/**
  * The tests in this class are really just assertions that they compile w/o error -- that the given
  * [[AsClient]] instances can be found in implicit scope
  */
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
      implicit val clientConf: ClientConfig = ClientConfig.load()

//      import agora.api.Implicits._

      val tem: ToEntityMarshaller[SomeCustomDto]       = implicitly[ToEntityMarshaller[SomeCustomDto]]
      val fem: FromEntityUnmarshaller[SomeResponseDto] = implicitly[FromEntityUnmarshaller[SomeResponseDto]]
      val m                                            = implicitly[Materializer]
      val ac2: AsClient[SomeCustomDto, SomeResponseDto] =
        asInferredClient[SomeCustomDto, SomeResponseDto](clientConf, m, tem, fem)

//      val ops: ClientConfigOps  = clientConf
      val ops1: ClientConfigOps = asConfigOps(clientConf)

      val t: AsClient[SomeCustomDto, SomeResponseDto] = ops1.typed[SomeCustomDto, SomeResponseDto]

      t should not be null

//      val asClient: AsClient[SomeCustomDto, SomeResponseDto]  = implicitly[AsClient[SomeCustomDto, SomeResponseDto]]
//      val asClient2: AsClient[SomeCustomDto, SomeResponseDto] = AsClient.instance[SomeCustomDto, SomeResponseDto]
//      asClient should not be null
    }
    "be able to produce an AsClient[SubmitJob, B] given an implicit FromEntityUnmarshaller and client config in scope" in {
      import io.circe.generic.auto._
      implicit val clientConf: ClientConfig = ClientConfig.load()

      val tem: ToEntityMarshaller[SubmitJob]           = implicitly[ToEntityMarshaller[SubmitJob]]
      val fem: FromEntityUnmarshaller[SomeResponseDto] = implicitly[FromEntityUnmarshaller[SomeResponseDto]]
      implicit val ac2: AsClient[SubmitJob, SomeResponseDto] =
        asInferredClient[SubmitJob, SomeResponseDto](clientConf, materializer, tem, fem)

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
