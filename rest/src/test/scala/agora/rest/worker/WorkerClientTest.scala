package agora.rest.worker

import agora.api.`match`.MatchDetails
import agora.api.worker.WorkerDetails
import agora.rest.BaseRoutesSpec

class WorkerClientTest extends BaseRoutesSpec {

  "WorkerClient" should {
    "be able to get health statistics" in {
      workerClient.health.futureValue.objectPendingFinalizationCount should be >= 0
    }
  }

  lazy val workerClient = {
    val worker        = WorkerRoutes()
    val restClient    = DirectRestClient(worker.routes)
    val workerDetails = WorkerDetails()
    val matchDetails  = MatchDetails.empty
    WorkerClient(restClient, "multipart", matchDetails, workerDetails)
  }
}
