package finance.rest.server

import org.scalatest.{Matchers, WordSpec}

class ServerConfigTest extends WordSpec with Matchers {
  "ServerConfig()" should {
    "read its configuration from reference.conf" in {
      ServerConfig().port shouldBe 1234
      ServerConfig().waitOnUserInput shouldBe true
    }
  }
}