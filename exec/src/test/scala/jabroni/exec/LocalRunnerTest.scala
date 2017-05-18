package jabroni.exec

import jabroni.exec.ProcessRunner.ProcessOutput
import jabroni.rest.BaseSpec
import jabroni.rest.test.TestUtils._
import org.scalatest.concurrent.ScalaFutures

class LocalRunnerTest extends BaseSpec with ScalaFutures {

  withMaterializer { implicit mat =>
    "LocalRunner" should {
      "execute" in {

        withTmpDir("localRunnerTest") { dir =>

          val runner = ProcessRunner(dir)

          val res: ProcessOutput = runner.run("bigOutput.sh".executable, srcDir.toAbsolutePath.toString, "1")
          val iter = res.futureValue.toStream
          // big output echos out all scala files, which should include this line
          // which we're about to test for ...
          iter.exists(_.contains("big output echos out all scala files, which should include this line")) shouldBe true
          iter.size should be > 100
        }
      }
    }
  }
}
