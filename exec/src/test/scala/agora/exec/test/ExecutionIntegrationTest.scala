package agora.exec.test

import java.nio.file.Path
import java.util.UUID

import agora.api.exchange.PendingSubscription
import agora.api.exchange.observer.{OnJobSubmitted, OnMatch, TestObserver}
import agora.time._
import agora.exec.client.{ExecutionClient, RemoteRunner}
import agora.exec.events._
import agora.exec.model._
import agora.exec.workspace.{UploadDependencies, WorkspaceClient, WorkspaceClientDelegate, WorkspaceId}
import agora.exec.{ExecBoot, ExecConfig}
import agora.rest.client.{AkkaClient, RestClient, RetryClient}
import agora.rest.{AkkaImplicits, HasMaterializer, RunningService}
import agora.{BaseIOSpec, BaseExecSpec}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import com.typesafe.scalalogging.StrictLogging
import org.scalatest.BeforeAndAfterEach
import org.scalatest.concurrent.Eventually

import scala.concurrent.Future
import scala.util.Properties

class ExecutionIntegrationTest extends BaseExecSpec with HasMaterializer with Eventually with BeforeAndAfterEach with StrictLogging { //with QueryClientScenarios {

  import ExecutionIntegrationTest._

  // just request 1 work item at a time in order to support the 'different servers' test
  var conf: ExecConfig                              = null
  var anotherConf: ExecConfig                       = null
  var server: RunningService[ExecConfig, ExecBoot]  = null
  var server2: RunningService[ExecConfig, ExecBoot] = null
  var client: RemoteRunner                          = null
  val started                                       = now()

  var directClient1: ExecutionClient = null
  var directClient2: ExecutionClient = null

  var clientObserverSystem: AkkaImplicits = null

  "RemoteRunner" should {
    "execute requests on different servers" in {
      verifyConcurrentServices("RemoteRunner" + UUID.randomUUID())
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
  def verifyConcurrentServices(suffix: String) = {

    val clientObserver: TestObserver = new TestObserver

    conf.exchangeConfig.connectObserver(clientObserver)(clientObserverSystem).futureValue

    // 0) verify basic connectivity of both servers
    {

      val directRes1: RunProcessResult = directClient1.run(RunProcess("echo", "directClient1").useCachedValueWhenAvailable(false)).futureValue
      val StreamingResult(out1)        = directRes1
      out1.mkString("") shouldBe "directClient1"

      awaitCalls1().foreach(_.size shouldBe 1)
      awaitCalls2().foreach(_.size shouldBe 0)

      val directRes2            = directClient2.run(RunProcess("echo", "directClient2").useCachedValueWhenAvailable(false)).futureValue
      val StreamingResult(out2) = directRes2
      out2.mkString("") shouldBe "directClient2"

      awaitCalls1().foreach(_.size shouldBe 1)
      awaitCalls2().foreach(_.size shouldBe 1)

      serverSideWorkspaces1.foreach(_.clear())
      serverSideWorkspaces2.foreach(_.clear())
    }

    val initialSow = eventually {

      val Some(sow) = clientObserver.stateOfTheWorld
      sow.stateOfTheWorld
    }
    initialSow.subscriptions.size shouldBe 2

    subscriptionsByServerPort() shouldBe Map(FirstPort -> 1, SecondPort -> 1)

    // 1) execute req 1
    val beforeJobOne = now()
    val workspace1   = "workspaceOne" + suffix
    val selectionFuture1 =
      client.run(
        RunProcess("cat", "file1.txt")
          .withWorkspace(workspace1)
          .withCaching(false)
          .useCachedValueWhenAvailable(false)
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

    eventually {
      withClue("The first exec jobs should be awaiting its dependencies") {
        awaitCalls1().foreach {
          case List(first) =>
            first.dependencies.dependsOnFiles shouldBe Set("file1.txt")
            first.dependencies.workspace shouldBe workspace1
        }
      }
    }

    // 2) execute req 2. Note we use the same exchange client for both requests ... one will be routed to our first
    // worker, the second request to the other (anotherServer...)

    val beforeJobTwo = now()
    val workspace2   = "workspaceTwo" + suffix
    val selectionFuture2: Future[RunProcessResult] =
      client.run(
        RunProcess("cat", "file2.txt")
          .withWorkspace(workspace2)
          .withCaching(false)
          .useCachedValueWhenAvailable(false)
          .withDependencies(Set("file2.txt"), testTimeout))

    withClue("both jobs should've been observed to have been received") {
      eventually {
        val List(job2: OnJobSubmitted, job1: OnJobSubmitted) = clientObserver.jobSubmissions()
        job2.jobSubmitted.submissionDetails.awaitMatch shouldBe true
        job1.jobSubmitted.submissionDetails.awaitMatch shouldBe true
      }
    }

    withClue("both jobs should've been matched") {
      eventually {
        val List(onMatch2: OnMatch, onMatch1: OnMatch) = clientObserver.matches()
        val Seq(workerCandidate1)                      = onMatch1.selection
        workerCandidate1.remaining shouldBe 0
        val Seq(workerCandidate2) = onMatch2.selection
        workerCandidate2.remaining shouldBe 0

        Set(workerCandidate1.subscription.details.location.port, workerCandidate2.subscription.details.location.port) shouldBe Set(SecondPort, FirstPort)
      }
    }

    selectionFuture1.isCompleted shouldBe false
    selectionFuture2.isCompleted shouldBe false

    eventually {
      withClue(s"At this point both jobs should've been picked up and the subscriptions drained: $clientObserver") {
        val queueWithTwoJobsPending = client.exchange.queueState().futureValue
        queueWithTwoJobsPending.jobs shouldBe empty
        queueWithTwoJobsPending.subscriptions.size shouldBe 2
        queueWithTwoJobsPending.subscriptions.foreach { pendingSubscription =>
          pendingSubscription.requested shouldBe 0
        }
      }
    }

    def verifyRunning = {
      val NotFinishedBetweenResponse(List(jobOnePending)) =
        conf.queryClient().notFinishedBetween(NotFinishedBetween(beforeJobOne, now(), verbose = true)).futureValue
      val NotFinishedBetweenResponse(List(jobTwoPending)) =
        anotherConf.queryClient().notFinishedBetween(NotFinishedBetween(beforeJobOne, now(), verbose = true)).futureValue

      val a = jobOnePending.details.get.job
    }

    withClue("Both exec jobs should be awaiting their dependencies") {
      eventually {
        awaitCalls1().foreach { calls =>
          calls.size shouldBe 1
          val List(awaitCall: AwaitCall) = calls
          awaitCall.dependencies.dependsOnFiles shouldBe Set("file1.txt")

        }

        uploadCalls1().foreach(_ shouldBe empty)
      }
      eventually {
        awaitCalls2().foreach { calls =>
          calls.size shouldBe 1
          val List(awaitCall: AwaitCall) = calls
          awaitCall.dependencies.dependsOnFiles shouldBe Set("file2.txt")

        }
        uploadCalls2().foreach(_ shouldBe empty)
      }
    }

    // verify querying jobs

    // 3) upload file1.txt dependency by targeting the worker directly to ensure 'resultFuture1' can now complete
    val (firstClient, theOtherClient) = portWhichFirstJobTakenByService match {
      case FirstPort  => directClient1 -> directClient2
      case SecondPort => directClient2 -> directClient1
    }
    val file1 = Upload.forText("file1.txt", "I'm file one")
    firstClient.upload(workspace1, file1).futureValue shouldBe true

    eventually {
      uploadCalls1().foreach {
        case List(firstUpload) => firstUpload.fileName shouldBe "file1.txt"
      }
    }

    // request 1 should now complete, as we've made sure both workers have file1.txt in workspace x

    val StreamingResult(result1Output) = selectionFuture1.futureValue
    result1Output.mkString("") shouldBe "I'm file one"

    // 4) upload file2.
    val file2 = Upload.forText("file2.txt", "I'm file two")
    theOtherClient.upload(workspace2, file2).futureValue shouldBe true

    //    eventually {
    //      val List(firstUpload) = uploadCalls1()
    //      firstUpload.fileName shouldBe "file1.txt"
    //      val List(secondUpload) = uploadCalls2()
    //      secondUpload.fileName shouldBe "file2.txt"
    //    }
    val StreamingResult(result2Output) = selectionFuture2.futureValue
    result2Output.mkString("") shouldBe "I'm file two"

    val readyForMoreWorkQueue = client.exchange.queueState().futureValue
    readyForMoreWorkQueue.jobs shouldBe empty
    readyForMoreWorkQueue.subscriptions.size shouldBe 2
    readyForMoreWorkQueue.subscriptions.foreach { pendingSubscription =>
      pendingSubscription.requested shouldBe 1
    }

  }

  def uploadCalls1() = serverSideWorkspaces1.map(_.uploadCalls())

  def awaitCalls1() = serverSideWorkspaces1.map(_.awaitCalls())

  def uploadCalls2() = serverSideWorkspaces2.map(_.uploadCalls())

  def awaitCalls2() = serverSideWorkspaces2.map(_.awaitCalls())

  def serverSideWorkspaces1: Option[TestWorkspaceClient] = {
    Option(server).map(_.service.workspaceClient).collect {
      case twc: TestWorkspaceClient =>
        twc.ensuring(_ == conf.workspaceClient, s"$twc != ${conf.workspaceClient}")
        twc
    }
  }

  def serverSideWorkspaces2 = {
    Option(server2).map(_.service.workspaceClient).collect {
      case twc: TestWorkspaceClient =>
        twc.ensuring(_ == anotherConf.workspaceClient, s"$twc != ${anotherConf.workspaceClient}")
        twc
    }
  }

  // verify we have 2 subscriptions
  def subscriptionsByServerPort() = {
    val queue = client.exchange.queueState().futureValue
    queue.subscriptions.map {
      case PendingSubscription(_, sub, requested) =>
        sub.details.location.port -> requested
    }.toMap
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    serverSideWorkspaces1.foreach(_.clear())
    serverSideWorkspaces2.foreach(_.clear())
  }

  def threadNames(): Set[String] = {
    AkkaImplicits.allThreads().map { t =>
      s"${t.getName} (daemon=${t.isDaemon}, alive=${t.isAlive})"
    }
  }

  var initialThreads: Set[String] = threadNames()
//  var beforeThreads: Set[String]  = Set()

  override def beforeAll(): Unit = {
    super.beforeAll()
//    beforeThreads = threadNames()

    anotherConf = ExecutionIntegrationTest.anotherConfig
    conf = ExecutionIntegrationTest.firstConfig

    clientObserverSystem = conf.clientConfig.newSystem("test-observer")

    anotherConf.initialRequest shouldBe 1

    server = conf.start().futureValue
    server2 = anotherConf.start().futureValue

    client = conf.remoteRunner()

    directClient1 = conf.executionClient()
    directClient2 = anotherConf.executionClient()
  }

  override def afterAll(): Unit = {
    super.afterAll()

    conf.stop().futureValue
    anotherConf.stop().futureValue
    if (server != null) {
      server.stop().futureValue
    }
    if (server2 != null) {
      server2.stop().futureValue
    }
    clientObserverSystem.stop().futureValue
    client.close()
    directClient1.stop().futureValue
    directClient2.stop().futureValue
    server = null
    server2 = null
    client = null
    directClient1 = null
    directClient2 = null
    clientObserverSystem = null

    val afterThreads: Set[String] = threadNames()
    val remaining                 = afterThreads -- beforeThreads.map(_.getName)

    def fmt(name: String, threads: Set[String]) = {
      threads.toList.sorted.mkString(s"\n\t$name\n\t", "\n\t", "\n\t")
    }

    val msg =
      s"""
         |${fmt("Remaining Threads", remaining)}
         |
        |${fmt("Initial Threads", initialThreads)}
         |
        |${fmt("Before Threads", beforeThreads.map(_.getName))}
         |
        |${fmt("After Threads", afterThreads)}
      """.stripMargin
    logger.info(msg)

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

object ExecutionIntegrationTest {

  // an attempt to choose a port based on the scala version as a little guard against running multiple scala version tests concurrently
  val FirstPort = {
    val digits = (Properties.versionString + "1111").filter(_.isDigit)
    val p      = digits.take(4).toInt % 1000
    7000 + p
  }
  val SecondPort = FirstPort + 1000

  lazy val dirName  = BaseIOSpec.nextTestDir("ExecutionIntegrationTest")
  lazy val dir2Name = BaseIOSpec.nextTestDir("ExecutionIntegrationTest")

  def firstConfig = {
    val underlyingConfig = ExecConfig(s"port=$FirstPort", s"exchange.port=$FirstPort", "initialRequest=1", s"workspaces.dir=$dirName")
    new ExecConfig(underlyingConfig.config) {
      override lazy val workspaceClient: TestWorkspaceClient = {
        new TestWorkspaceClient(underlyingConfig.workspaceClient)
      }
    }
  }

  def anotherConfig = {

    val underlyingConfig = ExecConfig(
      s"port=$SecondPort",
      s"exchange.port=$FirstPort",
      s"workspaces.dir=${dir2Name.toAbsolutePath.toString}",
      "initialRequest=1",
      "includeExchangeRoutes=false"
    )

    new ExecConfig(underlyingConfig.config) {
      override lazy val workspaceClient: TestWorkspaceClient = {
        new TestWorkspaceClient(underlyingConfig.workspaceClient)
      }
    }
  }

  /** represents an invocation of workspaceClient.upload(...)
    */
  case class AwaitCall(dependencies: UploadDependencies) {
    private var futureOpt: Option[Future[Path]] = None

    def setResult(f: Future[Path]) = {
      require(futureOpt.isEmpty)
      futureOpt = Option(f)
      f
    }

    def result = futureOpt
  }

  case class UploadCall(workspaceId: WorkspaceId, fileName: String, src: Source[ByteString, Any]) {
    private var futureOpt: Option[Future[(Long, Path)]] = None

    def setResult(f: Future[(Long, Path)]) = {
      require(futureOpt.isEmpty)
      futureOpt = Option(f)
      f
    }

    def result = futureOpt
  }

  /**
    * Wrap the server-side WS client as a means to test/assert calls received
    *
    * @param underlying
    */
  class TestWorkspaceClient(override val underlying: WorkspaceClient) extends WorkspaceClientDelegate {

    private object UploadLock

    private var uploadCallList = List[UploadCall]()

    private object AwaitLock

    private var awaitCallList = List[AwaitCall]()

    def awaitCalls() = AwaitLock.synchronized(awaitCallList)

    def uploadCalls() = UploadLock.synchronized(uploadCallList)

    def clear() = {
      UploadLock.synchronized(uploadCallList = Nil)
      AwaitLock.synchronized(awaitCallList = Nil)
    }

    override def awaitWorkspace(dependencies: UploadDependencies): Future[Path] = {
      val call = AwaitCall(dependencies)
      AwaitLock.synchronized {
        awaitCallList = call :: awaitCallList
      }
      val f = super.awaitWorkspace(dependencies)
      call.setResult(f)
    }

    override def upload(workspaceId: WorkspaceId, fileName: String, src: Source[ByteString, Any]): Future[(Long, Path)] = {
      val call = UploadCall(workspaceId, fileName, src)
      UploadLock.synchronized {
        uploadCallList = call :: uploadCallList
      }
      call.setResult(super.upload(workspaceId, fileName, src))
    }
  }

}
