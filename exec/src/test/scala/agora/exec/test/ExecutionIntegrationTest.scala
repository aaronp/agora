package agora.exec.test

import agora.exec.ExecConfig
import agora.exec.model.Upload
import agora.exec.rest.ExecutionRoutes
import agora.exec.run.{RemoteRunner, UploadClient}
import agora.rest.{BaseSpec, RunningService}

import scala.util.Properties

class ExecutionIntegrationTest extends BaseSpec {

  val conf                                                = ExecConfig()
  var server: RunningService[ExecConfig, ExecutionRoutes] = null
  var client: RemoteRunner                                = null

  "RemoteRunner" should {
    "be able to execute simple commands against a running server" in {
      val result: Iterator[String] = client.run("echo", "this", " is", " a", " test").futureValue
      result.mkString("") shouldBe "this is a test"
    }
    "be able to target an execution service after submitting a job to the exchange" in {
      val (execClient, output) = client.runAndSelect("whoami").futureValue

      output.mkString("") shouldBe Properties.userName

      execClient.uploader.upload("someTestDir", Upload.forText("hello.txt", "there")).futureValue shouldBe true
    }
  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    import agora.domain.RichConfig.implicits._
    val entries = conf.uniqueEntries
    entries.foreach(println)
    println(conf)
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
