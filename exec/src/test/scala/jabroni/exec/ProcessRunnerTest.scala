package jabroni.exec

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import jabroni.rest.BaseSpec

class ProcessRunnerTest extends BaseSpec {

  implicit val sys = ActorSystem()
  implicit val mat = ActorMaterializer()

  import jabroni.domain.io.implicits._

  "ProcessRunner.run" should {
    "return the output of a job and write it to file" in {

      import jabroni.rest.test.TestUtils._
      withTmpDir("process-runner-test") { dir =>
        val res = ProcessRunner(dir).run("echo", "hello world").futureValue
        res.toList shouldBe List("hello world")
        dir.resolve("std.out").text shouldBe "hello world\n"
      }
    }
    "be able to access env variables" in {

      import jabroni.rest.test.TestUtils._
      withTmpDir("process-runner-test-2") { dir =>
        val res = ProcessRunner(dir).run(RunProcess("/bin/bash", "-c", "echo FOO is $FOO").withEnv("FOO", "bar"), Nil).futureValue
        res.toList shouldBe List("FOO is bar")
        dir.resolve("std.out").text shouldBe "FOO is bar\n"
      }
    }
  }
}
