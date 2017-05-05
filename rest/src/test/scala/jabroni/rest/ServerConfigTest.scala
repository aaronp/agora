package jabroni.rest

import org.scalatest.{Matchers, WordSpec}

class ServerConfigTest extends WordSpec with Matchers {
  "ServerConfig()" should {
    "read its configuration from reference.conf" in {
      val default = ServerConfig.defaultConfig("jabroni.server")
      ServerConfig(default).port shouldBe 1234
      ServerConfig(default).waitOnUserInput shouldBe false
    }
  }
}
