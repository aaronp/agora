package agora.api.json

import agora.BaseSpec
import com.typesafe.config.{Config, ConfigFactory}

import scala.util.{Failure, Success, Try}

class JsonByteImplicitsTest extends BaseSpec with JsonByteImplicits {

  import io.circe.generic.auto._
  import io.circe.parser._
  import io.circe.syntax._

  "JsonByteImplicits" should {
    "be able to encode/decode Try success values" in {
      val tri: Try[Int] = Success(123)
      val json = tri.asJson
      decode[Try[Int]](json.noSpaces) shouldBe Right(tri)
    }
    "be able to encode/decode Try failure values" in {
      val tri: Try[Int] = Failure(new Exception("bang"))
      val json = tri.asJson
      val Right(Failure(err)) = decode[Try[Int]](json.noSpaces)
      err.getMessage shouldBe "bang"
    }
    "be able to encode/decode Config success values" in {
      val conf = ConfigFactory.parseString("hello : world")
      val json = conf.asJson
      decode[Config](json.noSpaces).right.get.getString("hello") shouldBe "world"
    }
  }
}
