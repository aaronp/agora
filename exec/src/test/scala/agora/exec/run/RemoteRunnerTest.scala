package agora.exec.run

import java.nio.charset.StandardCharsets._

import agora.api.exchange.JobPredicate
import agora.exec.ExecConfig
import agora.exec.model.{RunProcess, Upload}
import agora.exec.rest.{ExecutionHandler, ExecutionRoutes}
import agora.exec.workspace.WorkspaceId
import agora.rest.test.TestUtils._
import agora.rest.{BaseSpec, RunningService}
import org.scalatest.{AppendedClues, BeforeAndAfterAll}

class RemoteRunnerTest extends BaseSpec with BeforeAndAfterAll with AppendedClues with ProcessRunnerTCK {

  var runningWorker: RunningService[ExecConfig, ExecutionRoutes] = null
  var remoteRunner: ProcessRunner with AutoCloseable             = null

  def runner: ProcessRunner = remoteRunner

  "manually enabled load test" ignore {

    "stream a whole lot of results" in {
      val firstResults = remoteRunner.run("bigOutput.sh".executable, srcDir.toAbsolutePath.toString, "1").futureValue
      firstResults.size should be >= 10000
    }
  }

  "RemoteRunner.prepare" should {
    "match subscriptions" in {
      val runProcess                          = RunProcess(List("cat", "file.one"))
      val workspaceIdOpt: Option[WorkspaceId] = Option("session a")
      val fileDependencies: Set[String]       = Set("file.one")
      val job                                 = ExecutionClient.prepare(runProcess, workspaceIdOpt, fileDependencies)

      val subscription = ExecutionHandler.newWorkspaceSubscription("execKey", "session a", Set("file.one"))

      val matcher = JobPredicate()
      matcher.matches(job, subscription) shouldBe true

    }
  }

  override def beforeAll = startAll

  override def afterAll = stopAll

  val conf = ExecConfig()

  def startAll = {
    runningWorker = conf.start().futureValue
    remoteRunner = conf.remoteRunner(None, Set.empty)
  }

  def stopAll = {
    runningWorker.close()
    remoteRunner.close()
  }

}
