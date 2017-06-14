package jabroni.exec.test

import cucumber.api.{DataTable, PendingException, Scenario}
import cucumber.api.scala.{EN, ScalaDsl}
import jabroni.exec.model.RunProcess
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

  When("""^client (.*) executes job (.*) with command '(.*)' and the tags$""") { (clientId: String, jobId: String, command: String, tagTable: DataTable) =>
    val tags = tagTable.toMap.map { row =>
      row("key") -> row("value")
    }
    val commands: List[String] = command.split(" ", -1).toList
    val job = RunProcess(commands).withMetadata(tags.toMap)
    state = state.executeRunProcess(clientId, jobId, job, Nil)
  }
  When("""^We search jobs with metadata for '(.*)' = '(.*)'$""") { (key: String, value: String) =>
    state = state.searchMetadata(key, value)
  }
  Then("""^no job ids should be returned$""") { () =>
    state = state.verifySearch(Set.empty)
  }
  Then("""^we should find jobs? (.*)$""") { (jobIdString: String) =>
    val expectedIds = jobIdString.replaceAllLiterally(" and ", "").split("\\s*,\\s*", -1).toSet
    state = state.verifySearch(expectedIds)
  }
  Then("""^listing the metadata should return$""") { (tagTable: DataTable) =>
    val expected: Map[String, List[String]] = tagTable.toMap.foldLeft(Map[String, List[String]]()) {
      case (multimap, row) =>
        row.foldLeft(multimap) {
          case (mm, (key, value)) =>
            val newList = value :: mm.getOrElse(key, Nil)
            mm.updated(key, newList.sorted)
        }
    }
    state = state.verifyListingMetadata(expected)
  }

  Before { scen: Scenario =>
    state = ExecState()
  }

  After { scen: Scenario =>
    state = state.close()
  }
}
