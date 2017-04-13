package jabroni.rest.test

import com.typesafe.config.ConfigFactory
import cucumber.api.scala.{EN, ScalaDsl}
import jabroni.rest.server.ServerConfig
import org.scalatest.Matchers

class ServerSteps extends ScalaDsl with EN with Matchers {

  var state = ServerTestState()

  Given("""^the server configuration$""") { (config: String) =>
    val c = ConfigFactory.parseString(config).withFallback(ServerConfig.defaultConfig)
    state = state.copy(Option(ServerConfig(c)))
  }

  When("""^I start the server$""") {
    state = state.startServer()
  }

  After { _ =>
    // meh
  }

}
