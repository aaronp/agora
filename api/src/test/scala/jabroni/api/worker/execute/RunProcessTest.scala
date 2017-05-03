package jabroni.api.worker.execute

import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpec}

import scala.util.Properties

class RunProcessTest extends WordSpec with Matchers with ScalaFutures {

  "RunProcess.run" should {
    "run stuff" in {

      val lines = RunProcess().run("pwd").futureValue.toList
      lines.head shouldBe (Properties.userDir)
    }
  }
}
