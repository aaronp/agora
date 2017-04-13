package jabroni.rest.test

import com.typesafe.config.ConfigFactory._
import cucumber.api.DataTable
import cucumber.api.scala.{EN, ScalaDsl}
import jabroni.rest.client.ClientConfig
import org.scalatest.Matchers

class ClientSteps extends ScalaDsl with EN with Matchers with TestData {

  var state = {
    ClientTestState()
  }

  When("""^I connect a client$""") {
    state = state.connect()
  }

  Given("""^the client configuration$""") { (config: String) =>
    val c = parseString(config).withFallback(ClientConfig.defaultConfig)
    state = state.copy(config = Option(ClientConfig(c)))
  }

}
