package agora.exec.model

import agora.BaseSpec
import io.circe.generic.auto._
import io.circe.parser._
import io.circe.syntax._

class RunProcessTest extends BaseSpec {
  "RunProcess.resolveEnv" should {
    "replace dollar-side delimited keys in the command strings" in {
      val runProcess = RunProcess(List("$KEY/$KEYS/$KEY/x/$KEY"), Map("KEY" -> "foo"))
      runProcess.resolveEnv.command shouldBe List("foo/$KEYS/foo/x/foo")
    }
  }

  "RunProcess.toJson" should {
    "marshal and unmarshal streaming processes" in {

      val expected = RunProcess("foo")
      val json     = expected.asJson
      decode[RunProcess](json.noSpaces) shouldBe Right(expected)
    }
    "marshal and unmarshal result saving processes" in {
      val expected: RunProcess = RunProcess("foo").withStdOutTo("save me here")
      val json                 = expected.asJson
      val actual               = decode[RunProcess](json.noSpaces)
      actual shouldBe Right(expected)
    }
  }

}
