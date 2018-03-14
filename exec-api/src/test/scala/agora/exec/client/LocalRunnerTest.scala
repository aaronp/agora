package agora.exec.client

import agora.BaseExecApiSpec
import agora.exec.log.IterableLogger
import agora.exec.model.{RunProcess, StreamingResult}
import agora.rest.HasMaterializer
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}

import scala.collection.mutable.ListBuffer
import scala.sys.process.ProcessLogger

class LocalRunnerTest extends BaseExecApiSpec with ProcessRunnerTCK with BeforeAndAfter with BeforeAndAfterAll with HasMaterializer {

  "LocalRunner.run" should {
    "replace environment variables in the command argument" in {
      val rp                      = RunProcess(List("$TEST_COMMAND", "$VALUE world"), Map("TEST_COMMAND" -> "echo", "VALUE" -> "hello"))
      val runner                  = new LocalRunner(None)
      val StreamingResult(output) = runner.run(rp).futureValue
      output.mkString(" ").trim shouldBe "hello world"
    }
  }
  "LocalRunner.execute" should {

    "create a runner w/ the new runner 1" in {

      withDir { dir =>
        val stdOutFile = dir.resolve("justStdOut")
        val outputList = ListBuffer[String]()

        val runner = new LocalRunner(Option(dir))

        val file = dir.resolve("someFile").text = "hello world"

        val runProcess   = RunProcess("cat", file.fileName)
        val iterLogger   = IterableLogger(runProcess)
        val stdOutLogger = ProcessLogger(stdOutFile.toFile)
        val out = ProcessLogger(
          o => {
            outputList += o
          },
          e => {}
        )
        iterLogger
          .addStdOut(stdOutLogger)
          .add(out)

        val iter     = iterLogger.iterator
        val exitCode = runner.execute(runProcess, iterLogger).futureValue

        val output = iter.mkString(" ").trim
        output shouldBe "hello world"
        stdOutFile.text.lines.mkString(" ").trim shouldBe "hello world"
        outputList.toList shouldBe List("hello world")
      }
    }

    "create a runner w/ the new runner" in {

      withDir { dir =>
        val stdOutFile = dir.resolve("justStdOut")
        val outputList = ListBuffer[String]()
        val runner     = new LocalRunner(Option(dir))

        val fromFile = dir.resolve("from").text = "hello world"

        // call the method under test
        val runProcess = RunProcess("cp", fromFile.fileName, "newFile")
        val iter       = IterableLogger(runProcess)

        val stdOutLogger = ProcessLogger(stdOutFile.toFile)

        val out = ProcessLogger(
          o => {
            outputList += o
          },
          e => {}
        )
        iter
          .addStdOut(stdOutLogger)
          .add(out)

        // verify it worked
        runner.execute(runProcess, iter).futureValue

        stdOutFile.exists() shouldBe true
        dir.resolve("newFile").text shouldBe "hello world"
      }
    }
  }
  override def runner = {
    val dir = "target/localRunnerTest".asPath.mkDirs()
    ProcessRunner(Option(dir))
  }

}
