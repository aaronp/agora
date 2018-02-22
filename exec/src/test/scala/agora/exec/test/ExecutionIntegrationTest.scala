package agora.exec.test

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

import agora.api.exchange.PendingSubscription
import agora.api.exchange.observer.TestObserver
import agora.exec.ExecConfig
import agora.exec.client.RemoteRunner
import agora.exec.model.{RunProcess, StreamingResult, Upload}
import agora.exec.rest.ExecutionRoutes
import agora.rest.client.{AkkaClient, RestClient, RetryClient}
import agora.rest.logging.LoggingOps
import agora.rest.{HasMaterializer, RunningService}
import agora.{BaseIOSpec, BaseSpec}
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.Eventually

import scala.util.Properties

class ExecutionIntegrationTest extends BaseSpec with HasMaterializer with Eventually with BeforeAndAfterEach {

  // an attempt to choose a port based on the scala version as a little guard against running multiple scala version tests concurrently
  val FirstPort = {
    val digits = (Properties.versionString + "1111").filter(_.isDigit)
    val p      = digits.take(4).toInt % 1000
    7000 + p
  }
  val SecondPort = FirstPort + 1000

  // just request 1 work item at a time in order to support the 'different servers' test
  var conf: ExecConfig                                    = null
  var server: RunningService[ExecConfig, ExecutionRoutes] = null
  var client: RemoteRunner                                = null

  "RemoteRunner" should {
    "execute requests on different servers" in {
      withDir { dir =>
        val anotherConf: ExecConfig =
          ExecConfig(s"port=$SecondPort",
                     s"exchange.port=$FirstPort",
                     s"uploads.dir=${dir.toAbsolutePath.toString}",
                     "initialRequest=1",
                     "includeExchangeRoutes=false")
        anotherConf.initialRequest shouldBe 1

        val anotherServerConnectedToServer1Exchange = anotherConf.start().futureValue

        try {
          verifyConcurrentServices(anotherConf, "RemoteRunner" + UUID.randomUUID())
        } finally {
          anotherServerConnectedToServer1Exchange.stop().futureValue
          anotherConf.stop().futureValue
        }
      }
    }
  }

  /**
    * In this test, we want to check we can run two different jobs which get routed to the 2 different servers.
    *
    * To do this we:
    * 0) start two workers, each asking for only 1 work item
    * 1) submit a job with a dependency on file 1, which will get picked up by one of the workers (and decrement
    * that worker's requested count to 0)
    * 2) submit a job with a dependency on file 2. this should get routed to a different worker from #1
    * 3) upload file 1
    * 4) upload file 2
    * 5) verify jobs 1 and 2 complete on different servers
    *
    * We know job #1 can't complete as it's awaiting its upload file dependency on the server. This way
    * when job 2 is submitted it will get directed to the 2nd worker
    */
  def verifyConcurrentServices(anotherConf: ExecConfig, suffix: String) = {
    LoggingOps.withLogs("agora.exec.workspace.WorkspaceActor", "info") {
      case Some(appender) =>
        try {
          doVerifyConcurrentServices(anotherConf, suffix)
        } catch {
          case exp: Throwable =>
            val msg = appender.logs.mkString(s"#### Failed w/ ${exp.getMessage} ####\n", "\n", "\n")
            withClue(msg) {
              throw exp
            }

        }
      case other => sys.error(s"Not using logback: $other")
    }
  }

  def doVerifyConcurrentServices(anotherConf: ExecConfig, suffix: String) = {

    val clientObserver: TestObserver = new TestObserver
    conf.exchangeConfig.connectObserver(clientObserver).futureValue

    val initialSow = eventually {

      val Some(sow) = clientObserver.stateOfTheWorld
      sow.stateOfTheWorld
    }
    initialSow.subscriptions.size shouldBe 2

    // verify we have 2 subscriptions
    def subscriptionsByServerPort() = {
      val queue = client.exchange.queueState().futureValue
      queue.subscriptions.map {
        case PendingSubscription(_, sub, requested) =>
          sub.details.location.port -> requested
      }.toMap
    }

    subscriptionsByServerPort() shouldBe Map(FirstPort -> 1, SecondPort -> 1)

    // 1) execute req 1
    val workspace1 = "workspaceOne" + suffix
    val workspace2 = "workspaceTwo" + suffix
    val selectionFuture1 =
      client.run(
        RunProcess("cat", "file1.txt")
          .withWorkspace(workspace1)
          .withDependencies(Set("file1.txt"), testTimeout))

    // figure out who got job #1
    val portWhichFirstJobTakenByService: Int = eventually {
      val queueAfterOneJobSubmitted = subscriptionsByServerPort
      if (queueAfterOneJobSubmitted == Map(FirstPort -> 1, SecondPort -> 0)) {
        SecondPort
      } else if (queueAfterOneJobSubmitted == Map(FirstPort -> 0, SecondPort -> 1)) {
        //
        FirstPort
      } else {
        fail(s"queue after submitted one job was $queueAfterOneJobSubmitted")
      }
    }

    eventually {
      val List(job1) = clientObserver.jobSubmissions()
      job1.jobSubmitted.submissionDetails.awaitMatch shouldBe true
    }

    // 2) execute req 2. Note we use the same exchange client for both requests ... one will be routed to our first
    // worker, the second request to the other (anotherServer...)
    val selectionFuture2 =
      client.run(
        RunProcess("cat", "file2.txt")
          .withWorkspace(workspace2)
          .withDependencies(Set("file2.txt"), testTimeout))

    withClue("both jobs should've been received") {
      eventually {
        val List(job2, job1) = clientObserver.jobSubmissions()
        job2.jobSubmitted.submissionDetails.awaitMatch shouldBe true
      }
    }

    withClue("both jobs should've been matched") {
      eventually {
        val List(job2, job1)      = clientObserver.matches()
        val Seq(workerCandidate1) = job1.selection
        workerCandidate1.remaining shouldBe 0
        val Seq(workerCandidate2) = job2.selection
        workerCandidate2.remaining shouldBe 0

        Set(workerCandidate1.subscription.details.location.port, workerCandidate2.subscription.details.location.port) shouldBe Set(SecondPort, FirstPort)
      }
    }

    selectionFuture1.isCompleted shouldBe false
    selectionFuture2.isCompleted shouldBe false

    def sep(tri: Int, c: Char) = (c.toString * 60) + s"  $tri  " + (c.toString * 60)

    val tries = new AtomicInteger(0)
    withClue(s"At this point both jobs should've been picked up and the subscriptions drained: $clientObserver") {
      val attempt = tries.incrementAndGet()
      println(s"\n${sep(attempt, 'v')}\n\n$clientObserver\n${sep(attempt, '^')}")
      val queueWithTwoJobsPending = client.exchange.queueState().futureValue
      queueWithTwoJobsPending.jobs shouldBe empty
      queueWithTwoJobsPending.subscriptions.size shouldBe 2
      queueWithTwoJobsPending.subscriptions.foreach { pendingSubscription =>
        pendingSubscription.requested shouldBe 0
      }
    }

    // 3) upload file1.txt dependency by targeting the worker directly to ensure 'resultFuture1' can now complete
    val directClient1 = conf.executionClient()
    val directClient2 = anotherConf.executionClient()
    val (firstClient, theOtherClient) = portWhichFirstJobTakenByService match {
      case FirstPort  => directClient1 -> directClient2
      case SecondPort => directClient2 -> directClient1
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

    val readyForMoreWorkQueue = client.exchange.queueState().futureValue
    readyForMoreWorkQueue.jobs shouldBe empty
    readyForMoreWorkQueue.subscriptions.size shouldBe 2
    readyForMoreWorkQueue.subscriptions.foreach { pendingSubscription =>
      pendingSubscription.requested shouldBe 1
    }

  }

  override def beforeAll(): Unit = {
    super.beforeAll()
    val dirName = BaseIOSpec.nextTestDir("ExecutionIntegrationTest")

    conf = ExecConfig(s"port=$FirstPort", s"exchange.port=$FirstPort", "initialRequest=1", s"workspaces.dir=$dirName")

    server = conf.start().futureValue
    client = conf.remoteRunner()
  }

  override def afterAll(): Unit = {
    super.afterAll()
    conf.stop().futureValue
    client.close()
    server.stop().futureValue
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
