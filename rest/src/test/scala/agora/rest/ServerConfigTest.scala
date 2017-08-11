package agora.rest

import com.typesafe.config.ConfigFactory

class ServerConfigTest extends BaseSpec {

  "ServerConfig.newSystem" should {
    "create a new akka actor system based on the relative config path and system name" in {
      val conf = new ServerConfig(ConfigFactory.load().getConfig("agora.exchange"))
      conf.newClientSystem.system.settings.Daemonicity shouldBe true
      conf.serverImplicits.system.settings.Daemonicity shouldBe false
    }
  }

}
