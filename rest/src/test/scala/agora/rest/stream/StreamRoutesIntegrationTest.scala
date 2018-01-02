package agora.rest.stream

import agora.BaseSpec
import agora.rest.client.StreamWebsocketClient
import agora.rest.{HasMaterializer, RunningService, ServerConfig}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalatest.BeforeAndAfterEach

class StreamRoutesIntegrationTest extends BaseSpec with FailFastCirceSupport with BeforeAndAfterEach with HasMaterializer {

  var serverConfig: ServerConfig = null
  var service: RunningService[ServerConfig, StreamRoutes] = null

  "StreamRoutes" should {
    "service websocket requests" in {
      StreamWebsocketClient(serverConfig.clientConfig.location)

    }
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    startAll()
  }

  override def afterEach(): Unit = {
    super.afterEach()
    stopAll()
  }

  def startAll() = {
    serverConfig = ServerConfig("host=localhost", s"port=7777")
    val sr = new StreamRoutes
    service = RunningService.start[ServerConfig, StreamRoutes](serverConfig, sr.routes, sr).futureValue
  }

  def stopAll() = {
    serverConfig.stop().futureValue
    service.stop().futureValue
  }
}