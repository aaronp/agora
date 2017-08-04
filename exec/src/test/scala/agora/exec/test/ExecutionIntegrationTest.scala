package agora.exec.test

import agora.exec.ExecConfig
import agora.exec.model.{RunProcess, Upload}
import agora.exec.rest.ExecutionRoutes
import agora.exec.run.RemoteRunner
import agora.rest.{BaseSpec, HasMaterializer, RunningService}

import scala.util.Properties

class ExecutionIntegrationTest extends BaseSpec with HasMaterializer {

  val conf                                                = ExecConfig()
  var server: RunningService[ExecConfig, ExecutionRoutes] = null
  var client: RemoteRunner                                = null

  "RemoteRunners" should {
    "run on different servers" in {
      withDir { dir =>
        val anotherServer = ExecConfig("port=8888", s"uploads.dir=${dir.toAbsolutePath.toString}", "initialExecutionSubscription=1").start()

        val (execClient, output) = client.runAndSelect("whoami").futureValue
        output.mkString("") shouldBe Properties.userName

      }
    }
  }
  "RemoteRunner" should {
    "be able to execute simple commands against a running server" in {
      val result = client.run("echo", "this", "is", "a", "test").futureValue
      result.mkString("") shouldBe "this is a test"
    }
    "be able to target an execution service after submitting a job to the exchange" in {

      // determine summat which can run our request
      val (execClient, output) = client.runAndSelect("whoami").futureValue
      output.mkString("") shouldBe Properties.userName

      // upload something to that client
      execClient.upload("someTestDir", Upload.forText("hello.txt", "there")).futureValue shouldBe true

      // execute something which uses needs that upload
      val result: Iterator[String] = client.run(RunProcess("cat", "hello.txt").withDependencies("someTestDir", Set("hello.txt"), testTimeout)).futureValue
      result.mkString("") shouldBe "there"
    }
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    server = conf.start().futureValue
    client = conf.remoteRunner()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    server.stop()
    client.close()
    server = null
    client = null
  }

}
