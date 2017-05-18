package jabroni.exec

import jabroni.rest.BaseSpec
import jabroni.rest.test.TestUtils.{withMaterializer, withTmpDir}

class ProcessRunnerTest extends BaseSpec {

  withMaterializer { implicit mat =>

    "ProcessRunner.run" should {
      "return the output of a job and write it to file" in {

        withTmpDir("process-runner-test") { dir =>
          val runner = ProcessRunner(dir, logDir = Option(dir))
          val res = runner.run("echo", "hello world").futureValue
          res.toList shouldBe List("hello world")
          dir.resolve("std.out").text shouldBe "hello world\n"
        }
      }
      "be able to access env variables" in {

        withTmpDir("process-runner-test-2") { dir =>
          val runner = ProcessRunner(dir, logDir = Option(dir))
          val res = runner.run(RunProcess("/bin/bash", "-c", "echo FOO is $FOO").withEnv("FOO", "bar"), Nil).futureValue
          res.toList shouldBe List("FOO is bar")
          dir.resolve("std.out").text shouldBe "FOO is bar\n"
        }
      }
    }
  }
}
