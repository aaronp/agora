package agora.exec.run

import agora.exec.model.RunProcessAndSave
import agora.rest.BaseSpec
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}

import scala.collection.mutable.ListBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.sys.process.ProcessLogger

class LocalRunnerTest extends BaseSpec with ProcessRunnerTCK with BeforeAndAfter with BeforeAndAfterAll {

  "LocalRunner.withLogger" should {
    "create a runner w/ the new runner 1" in {

      withDir { dir =>
        val stdOutFile = dir.resolve("justStdOut")
        val outputList = ListBuffer[String]()

        val runner = new LocalRunner(Option(dir)).withLogger { jobLog =>
          val stdOutLogger = ProcessLogger(stdOutFile.toFile)

          val out = ProcessLogger(
            o => {
              outputList += o
            },
            e => {}
          )
          jobLog
            .addStdOut(stdOutLogger)
            .add(out)
        }

        val file   = dir.resolve("someFile").text = "hello world"
        val output = runner.run("cat", file.fileName).futureValue.mkString(" ").trim
        output shouldBe "hello world"
        stdOutFile.text.lines.mkString(" ").trim shouldBe "hello world"
        outputList.toList shouldBe List("hello world")
      }
    }

    "create a runner w/ the new runner" in {

      withDir { dir =>
        val stdOutFile = dir.resolve("justStdOut")
        val outputList = ListBuffer[String]()
        val runner = new LocalRunner(Option(dir)).withLogger { jobLog =>
          val stdOutLogger = ProcessLogger(stdOutFile.toFile)

          val out = ProcessLogger(
            o => {
              outputList += o
            },
            e => {}
          )
          jobLog
            .addStdOut(stdOutLogger)
            .add(out)
        }

        val file = dir.resolve("from").text = "hello world"

        // call the method under test
        val resp = runner.runAndSave(RunProcessAndSave(List("cp", file.fileName, "newFile"), dir.fileName)).futureValue

        // verify it worked
        resp.exitCode shouldBe 0
        stdOutFile.exists shouldBe true
        dir.resolve("newFile").text shouldBe "hello world"
      }
    }
  }

  implicit val sys = ActorSystem()
  implicit val mat = ActorMaterializer()

  override def afterAll() = {
    mat.shutdown()
    sys.terminate()
  }

  override def runner = {
    val dir = "target/localRunnerTest".asPath.mkDirs()
    ProcessRunner(Option(dir))
  }

}
