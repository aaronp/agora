package agora.exec.test

import agora.exec.model.RunProcess
import agora.rest.test.TestData
import cucumber.api.scala.{EN, ScalaDsl}
import cucumber.api.{DataTable, Scenario}
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
  When("""^client (.*) executes job (.*) with command '(.*)' and the tags$""") { (clientId: String, jobId: String, command: String, tagTable: DataTable) =>
    val tags = tagTable.toMap.map { row =>
      row("key") -> row("value")
    }
    val commands: List[String] = command.split(" ", -1).toList
    val job                    = RunProcess(commands).withMetadata(tags.toMap)
    state = state.executeRunProcess(clientId, jobId, job)
  }
  Before { scen: Scenario =>
    state = ExecState()
  }

  // scalafmt wanted to put this after the closing brace above
  After { scen: Scenario =>
    state = state.close()
  }
}
