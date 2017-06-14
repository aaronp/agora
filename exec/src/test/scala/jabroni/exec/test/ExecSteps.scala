package jabroni.exec.test

import cucumber.api.Scenario
import cucumber.api.scala.{EN, ScalaDsl}
import jabroni.rest.test.TestData
import miniraft.state.NodeId
import org.scalatest.Matchers

class ExecSteps extends ScalaDsl with EN with Matchers with TestData {

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

  Before { scen : Scenario =>
    state = ExecState()
  }

  After { scen : Scenario =>
    state = state.close()
  }
}
