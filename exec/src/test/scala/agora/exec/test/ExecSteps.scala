package agora.exec.test

import agora.exec.ExecConfig
import agora.exec.model.RunProcess
import agora.rest.test.TestData
import akka.actor.ActorSystem
import akka.http.scaladsl.testkit.RouteTestTimeout
import com.typesafe.config.ConfigFactory
import com.typesafe.scalalogging.StrictLogging
import cucumber.api.Scenario
import cucumber.api.scala.{EN, ScalaDsl}
import miniraft.state.NodeId
import org.scalatest.Matchers
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}

import scala.concurrent.duration._

class ExecSteps extends ScalaDsl with EN with Matchers with TestData with ScalaFutures with StrictLogging {

  var state = ExecState()

  Given("""^A running executor service on port (.*)$""") { (port: String) =>
    state = state.startExecutorOnPort(port.toInt)
  }
  Given("""^Remote client (.*) connected to port (.*)$""") { (name: String, port: String) =>
    state = state.connectClient(name, port.toInt)
  }
  When("""^client (.*) executes$""") { (client: String, command: String) =>
    state = state.execute(client, command)
  }
  Then("""^the response text should be (.*)$""") { (expectedOutput: String) =>
    state = state.verifyExecResult(expectedOutput)
  }
  When("""^we kill the actor system for client (.*)$""") { (nodeId: NodeId) =>
    state = state.stopClient(nodeId)
  }
  Given("""^an executor service (.*) started with config$""") { (serverName: String, customConfig: String) =>
    val conf = ExecConfig().withOverrides(ConfigFactory.parseString(customConfig))
    val runningService = conf.start().futureValue
    state = state.withService(serverName, runningService)
  }
  Given("""^executor client (.*) connects to (.*)$""") { (clientName: String, serverName: String) =>
    state = state.connectClient(clientName, serverName)
    require(state.clientsByName.contains(clientName))
  }
  When("""^client (.*) executes (.*)$""") { (clientName: String, executeText: String) =>
    val command = executeText.split(" ").map(_.trim).toList
    state = state.executeRunProcess(clientName, s"no job id for: ${command.mkString(" ")}", RunProcess(command))
  }

  Before { scen: Scenario =>
    state = ExecState()
  }

  // scalafmt wanted to put this after the closing brace above
  After { scen: Scenario =>
    logger.info(s"Closing after $scen")
    state = state.close()
  }

  /**
    * TODO - extend BaseSpec
    */
  implicit def testTimeout: FiniteDuration = 4.seconds

  implicit def default(implicit system: ActorSystem) = RouteTestTimeout(testTimeout)

  implicit override def patienceConfig =
    PatienceConfig(timeout = scaled(Span(testTimeout.toSeconds, Seconds)), interval = scaled(Span(150, Millis)))

}
