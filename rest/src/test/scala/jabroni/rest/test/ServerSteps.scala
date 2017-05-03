package jabroni.rest.test

import com.typesafe.config.ConfigFactory
import cucumber.api.scala.{EN, ScalaDsl}
import jabroni.rest.ServerConfig
import jabroni.rest.exchange.ExchangeRoutes
import org.scalatest.Matchers

class ServerSteps extends ScalaDsl with EN with Matchers {

  var state = ServerTestState()

  Given("""^the server configuration$""") { (config: String) =>
    val c = ConfigFactory.parseString(config).withFallback(ServerConfig.defaultConfig("jabroni.exchange"))
    state = state.copy(Option(ServerConfig(c)))
  }

  When("""^I start the exchange server$""") {
    state = state.startExchangeServer()
  }

  After { _ =>
    // meh
  }

}
