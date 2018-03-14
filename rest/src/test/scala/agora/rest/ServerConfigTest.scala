package agora.rest

import agora.BaseRestSpec
import com.typesafe.config.ConfigFactory

class ServerConfigTest extends BaseRestSpec {

  "ServerConfig.newSystem" should {
    "create a new akka actor system based on the relative config path and system name" in {
      val conf = new ServerConfig(ConfigFactory.load().getConfig("agora.exchange"))
      val dave = conf.clientConfig.newSystem("dave")
      try {
        dave.system.settings.Daemonicity shouldBe true
        conf.serverImplicits.system.settings.Daemonicity shouldBe false
      } finally {
        dave.stop().futureValue
        conf.stop().futureValue
      }
    }
  }
}
