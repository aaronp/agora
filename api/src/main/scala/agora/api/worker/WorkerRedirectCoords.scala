package agora.api.worker

import agora.api.config.HostLocation

case class WorkerRedirectCoords(workerLocation: HostLocation, subscriptionKey: SubscriptionKey, remainingItems: Int)
