package agora.rest.worker

import agora.api.`match`.MatchDetails
import agora.api.config.HostLocation
import agora.api.exchange.WorkSubscription
import agora.api.health.HealthDto
import agora.api.worker.WorkerDetails
import agora.rest.BaseRoutesSpec

class WorkerClientTest extends BaseRoutesSpec {

  "WorkerClient" should {
    "be able to get health statistics" in {

      val workerDetails = WorkerDetails(HostLocation.localhost(1234))
      val worker        = DynamicWorkerRoutes(WorkSubscription.forDetails(workerDetails))
      val restClient    = DirectRestClient(worker.routes)

      val workerClient: WorkerClient = {
        val matchDetails = MatchDetails.empty
        WorkerClient(restClient, matchDetails, workerDetails)
      }

      try {
        val health: HealthDto = workerClient.health.futureValue
        health.objectPendingFinalizationCount should be >= 0
        health.system.availableProcessors should be > 0
      } finally {
        restClient.stop().futureValue
      }
    }
  }
}
