package agora.exec.test

import java.util.UUID

import agora.BaseSpec
import agora.api.exchange.PendingSubscription
import agora.exec.ExecConfig
import agora.exec.model.{RunProcess, StreamingResult, Upload}
import agora.exec.rest.ExecutionRoutes
import agora.exec.client.RemoteRunner
import agora.rest.client.{AkkaClient, RestClient, RetryClient}
import agora.rest.{HasMaterializer, RunningService}
import org.scalatest.concurrent.Eventually

import scala.util.Properties

class ExecutionIntegrationTest extends BaseSpec with HasMaterializer with Eventually {

  // just request 1 work item at a time in order to support the 'different servers' test
  val conf                                                = ExecConfig("initialRequest=1")
  var server: RunningService[ExecConfig, ExecutionRoutes] = null
  var client: RemoteRunner                                = null

  "RemoteRunner" should {
    "execute requests on different servers" in {
      withDir { dir =>
        val anotherConf: ExecConfig = ExecConfig("port=8888", s"uploads.dir=${dir.toAbsolutePath.toString}", "initialRequest=1")
        anotherConf.initialRequest shouldBe 1
        val anotherServerConnectedToServer1Exchange = {
          anotherConf.start().futureValue
        }
        try {
          verifyConcurrentServices(anotherConf)
        } finally {
          anotherServerConnectedToServer1Exchange.stop().futureValue
        }
      }
    }
    "be able to execute simple commands against a running server" in {
      val result = client.stream("echo", "this", "is", "a", "test").futureValue.output
      result.mkString("") shouldBe "this is a test"
    }
  }

  /**
    * In this test, we want to check we can run two different jobs which get routed to the 2 different servers.
    *
    * To do this we:
    * 1) submit a job with a dependency on file 1
    * 2) submit a job with a dependency on file 2. this should get routed to a different worker from #1
    * 3) upload file 1
    * 4) upload file 2
    * 5) verify jobs 1 and 2 complete on different servers
    *
    * This works because we set up each server to only ask for one work item at a time. The jobs take a work item
    * from the exchange, but can't complete as they're blocking on their upload file dependencies. This way
    * when job 2 is submitted it will get directed to the 2nd worker
    */
  def verifyConcurrentServices(anotherConf: ExecConfig) = {

    // verify we have 2 subscriptions
    def subscriptionsByServerPort() = {
      val queue = client.exchange.queueState().futureValue
      queue.subscriptions.map {
        case PendingSubscription(_, sub, requested) =>
          sub.details.location.port -> requested
      }.toMap
    }

    subscriptionsByServerPort() shouldBe Map(7770 -> 1, 8888 -> 1)

    // 1) execute req 1
    val workspace1       = UUID.randomUUID().toString
    val workspace2       = UUID.randomUUID().toString
    val selectionFuture1 = client.run(RunProcess("cat", "file1.txt").withDependencies(workspace1, Set("file1.txt"), testTimeout))

    val portWhichFirstJobTakenByService: Int = eventually {
      val queueAfterOneJobSubmitted = subscriptionsByServerPort
      if (queueAfterOneJobSubmitted == Map(7770 -> 1, 8888 -> 0)) {
        8888
      } else if (queueAfterOneJobSubmitted == Map(7770 -> 0, 8888 -> 1)) {
        //
        7770
      } else {
        fail(s"queue after submitted one job was $queueAfterOneJobSubmitted")
      }
    }

    // 2) execute req 2. Note we use the same exchange client for both requests ... one will be routed to our first
    // worker, the second request to the other (anotherServer...)
    val selectionFuture2 = client.run(RunProcess("cat", "file2.txt").withDependencies(workspace2, Set("file2.txt"), testTimeout))

    selectionFuture1.isCompleted shouldBe false
    selectionFuture2.isCompleted shouldBe false

    // 3) upload file1.txt dependency. Normally we would get one of these after routing a 'selection' job via the
    // exchange, but here we just target the workers directly to ensure 'resultFuture1' can now complete
    val directClient1 = conf.executionClient()
    val directClient2 = anotherConf.executionClient()
    val (firstClient, theOtherClient) = portWhichFirstJobTakenByService match {
      case 7770 => directClient1 -> directClient2
      case 8888 => directClient2 -> directClient1
    }
    val file1 = Upload.forText("file1.txt", "I'm file one")
    firstClient.upload(workspace1, file1).futureValue shouldBe true

    // request 1 should now complete, as we've made sure both workers have file1.txt in workspace x

    val StreamingResult(result1Output) = selectionFuture1.futureValue
    result1Output.mkString("") shouldBe "I'm file one"

    // 4) upload file2.
    val file2 = Upload.forText("file2.txt", "I'm file two")
    theOtherClient.upload(workspace2, file2).futureValue shouldBe true
    val StreamingResult(result2Output) = selectionFuture2.futureValue
    result2Output.mkString("") shouldBe "I'm file two"
  }
  override def beforeAll(): Unit = {
    super.beforeAll()
    server = conf.start().futureValue
    client = conf.remoteRunner()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    server.stop()
    server = null
    client = null
  }

  def locationForClient(client: RestClient) = {
    client match {
      case retry: RetryClient =>
        retry.client match {
          case ac: AkkaClient => ac.location
          case other =>
            fail(s"Don't know how to get location from $other")
            ???
        }
      case ac: AkkaClient => ac.location
    }
  }
}
