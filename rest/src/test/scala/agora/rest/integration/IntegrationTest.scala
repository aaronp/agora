package agora.rest.integration

import agora.rest.exchange.{ExchangeRestServiceSpec, ExchangeWebsocketSpec}
import agora.rest.worker.WorkerIntegrationSpec

class IntegrationTest extends BaseIntegrationTest with ExchangeWebsocketSpec with ExchangeRestServiceSpec with WorkerIntegrationSpec
