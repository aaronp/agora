package agora.exec.workspace

import java.nio.file.Path

import agora.BaseSpec
import agora.api.exchange.{Exchange, WorkSubscription}
import agora.api.worker.WorkerDetails
import io.circe.Json
import io.circe.optics.JsonPath

class UpdatingWorkspaceClientTest extends BaseSpec {

  import UpdatingWorkspaceClient._

  "UpdatingWorkspaceClient.removeWorkspaceFromSubscription" should {
    "remove workspaces frmo the subscription" in {
      withDir { dir =>
        val scenario = TestScenario(dir)
        scenario.verifyAppendFirstWorkspace("removeMe")

      }
    }
  }
  "UpdatingWorkspaceClient.appendWorkspaceToSubscription" should {
    "add an initial workspace to a subscription" in {
      withDir { dir =>
        val scenario = TestScenario(dir)

        val before = scenario.verifyAppendFirstWorkspace("dave")
        scenario.verifyRemoveWorkspace(before, "dave")
      }
    }
    "add additional workspaces to a subscription" in {
      withDir { dir =>
        val scenario                  = TestScenario(dir)
        val newDetails: WorkerDetails = scenario.verifyAppendFirstWorkspace("foo")
        scenario.verifyAppendSecondWorkspace(newDetails, "foo", "bar")
      }
    }
  }

  case class TestScenario(dir: Path) {
    val exchange         = Exchange.instance()
    val original         = WorkSubscription.localhost(4444).withSubscriptionKey("test key")
    val subsdcriptionAck = exchange.subscribe(original).futureValue

    def verifyAppendSecondWorkspace(existingDetails: WorkerDetails, firstName: String, secondName: String) = {

      // let's go one more -- add a second workspace to the subscription
      val secondUpdate = appendWorkspaceToSubscription(exchange, subsdcriptionAck.id, secondName).futureValue
      secondUpdate.oldDetails shouldBe Some(existingDetails)

      val Some(newDetails) = secondUpdate.newDetails

      val workspacesOpt: Option[Vector[Json]] = JsonPath.root.workspaces.arr.getOption(newDetails.aboutMe)
      workspacesOpt.toVector.flatten.flatMap(_.asString) should contain only (firstName, secondName)

    }

    def verifyAppendFirstWorkspace(workspaceName: String) = {

      // call the method under test -- add a json entry of 'workspaces : [ workspaceName ]'
      val updateAck = appendWorkspaceToSubscription(exchange, subsdcriptionAck.id, workspaceName).futureValue
      updateAck.id shouldBe subsdcriptionAck.id
      updateAck.oldDetails shouldBe Some(original.details)

      val Some(newDetails) = updateAck.newDetails

      val workspacesOpt: Option[Vector[Json]] = JsonPath.root.workspaces.arr.getOption(newDetails.aboutMe)
      workspacesOpt.toVector.flatten.flatMap(_.asString) should contain only (workspaceName)

      newDetails
    }

    def verifyRemoveWorkspace(existingDetails: WorkerDetails, workspaceNameToRemove: String) = {

      // call the method under test -- remove a json entry of 'workspaces : [ workspaceName ]'
      val updateAck = removeWorkspaceFromSubscription(exchange, subsdcriptionAck.id, workspaceNameToRemove).futureValue
      updateAck.id shouldBe subsdcriptionAck.id
      updateAck.oldDetails shouldBe Some(existingDetails)

      val Some(newDetails) = updateAck.newDetails

      val workspacesOpt: Option[Vector[Json]] = JsonPath.root.workspaces.arr.getOption(newDetails.aboutMe)
      workspacesOpt.toVector.flatten.flatMap(_.asString) should not contain (workspaceNameToRemove)

      newDetails
    }
  }

}
