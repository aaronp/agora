package jabroni.rest.test

import com.typesafe.config.ConfigFactory
import cucumber.api.scala.{EN, ScalaDsl}
import jabroni.rest.{ExchangeMain, ServerConfig, WorkerMain}
import org.scalatest.Matchers

class ExchangeSteps extends ScalaDsl with EN with Matchers {

  var state = ExchangeTestState()

  Given("""^I start an exchange with command line (.*)$""") { (commandLine: String) =>
    val config = ExchangeMain.configForArgs(commandLine.split("\\s+", -1))
    state = state.startExchangeServer(config)
  }

  Given("""^I start a worker with command line (.*)$""") { (commandLine: String) =>
    val config = WorkerMain.configForArgs(commandLine.split("\\s+", -1))
    state = state.startWorker(config)
  }
  Given("""^I start a worker with config (.*)$""") { (configString: String) =>
    val config = {
      val conf = ConfigFactory.parseString(configString).withFallback(WorkerMain.defaultConfig)
      ServerConfig(conf)
    }
    state = state.startWorker(config)
  }

  When("""^worker (.*) creates a subscription to jobs matching jobs on (.*)$""") { (name : String, configString: String) =>
    ???
  }
  When("""^matching details on (.*)$""") { (name : String, configString: String) =>
    ???
  }


  After { _ =>
    // meh
  }

}
