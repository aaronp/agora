package agora.rest.worker

import agora.api.`match`.MatchDetails
import agora.api.exchange.WorkSubscription
import agora.api.worker.{HostLocation, WorkerDetails}
import agora.rest.BaseRoutesSpec

class WorkerClientTest extends BaseRoutesSpec {

  "WorkerClient" should {
    "be able to get health statistics" in {
      workerClient.health.futureValue.objectPendingFinalizationCount should be >= 0
    }
  }

  lazy val workerClient = {
    val workerDetails = WorkerDetails(HostLocation.localhost(1234))
    val worker        = DynamicWorkerRoutes(WorkSubscription.forDetails(workerDetails))
    val restClient    = DirectRestClient(worker.routes)
    val matchDetails  = MatchDetails.empty
    WorkerClient(restClient, "multipart", matchDetails, workerDetails)
  }
}
