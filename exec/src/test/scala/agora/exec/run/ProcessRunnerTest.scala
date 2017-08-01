package agora.exec.run

import agora.exec.log.IterableLogger
import agora.exec.model.RunProcess
import agora.rest.BaseSpec
import agora.rest.test.TestUtils.{withMaterializer, withTmpDir}

import scala.concurrent.ExecutionContext.Implicits.global

class ProcessRunnerTest extends BaseSpec {

  withMaterializer { implicit mat =>
    "ProcessRunner.run" should {
      "return the output of a job and write it to file" in {

        withTmpDir("process-runner-test") { dir =>
          val runner = ProcessRunner().withLogger(_.addUnderDir(dir))
          val res    = runner.run("echo", "hello world").futureValue
          res.toList shouldBe List("hello world")
          dir.resolve("std.out").text shouldBe "hello world\n"
        }
      }
      "be able to access env variables" in {

        withTmpDir("process-runner-test") { dir =>
          val logger = IterableLogger.forProcess(_)
          val runner = ProcessRunner().withLogger(_.addUnderDir(dir))
          val res    = runner.run(RunProcess("/bin/bash", "-c", "echo FOO is $FOO").withEnv("FOO", "bar")).futureValue
          res.toList shouldBe List("FOO is bar")
          dir.resolve("std.out").text shouldBe "FOO is bar\n"
        }
      }
    }
  }
}
