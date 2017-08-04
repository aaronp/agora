package agora.exec.test

import agora.exec.ExecConfig
import agora.exec.model.RunProcess
import agora.exec.rest.ExecutionRoutes
import agora.exec.run.RemoteRunner
import agora.rest.RunningService
import agora.rest.test.TestData
import com.typesafe.config.ConfigFactory
import cucumber.api.scala.{EN, ScalaDsl}
import org.scalatest.Matchers
import org.scalatest.concurrent.ScalaFutures

class ExecuteSteps extends ScalaDsl with EN with Matchers with ScalaFutures with TestData {

  import ExecuteSteps._

  var state = ExecuteState()

  Given("""^an executor service (.*) started with config$""") { (serverName: String, customConfig: String) =>
    val conf           = ExecConfig().withOverrides(ConfigFactory.parseString(customConfig))
    val runningService = conf.start().futureValue
    state = state.withService(serverName, runningService)
  }
  Given("""^executor client (.*) connects to$""") { (clientName: String, serverName: String) =>
    state = state.connectClient(clientName, serverName)
  }
  When("""^client (.*) executes (.*)$""") { (clientName: String, executeText: String) =>
    val command = executeText.split(" ").map(_.trim).toList
    val result  = state.clientByName(clientName).run(RunProcess(command)).futureValue
    val text    = result.mkString("")
    state = state.setLastResult(text)
  }
}

object ExecuteSteps {
  type Service = RunningService[ExecConfig, ExecutionRoutes]

  case class ExecuteState(serviceByName: Map[String, Service] = Map.empty, clientByName: Map[String, RemoteRunner] = Map.empty, lastResult: String = "") {
    def setLastResult(text: String) = copy(lastResult = text)

    def connectClient(clientName: String, serverName: String): ExecuteState = {
      val client: RemoteRunner = serviceByName(serverName).conf.remoteRunner()
      copy(clientByName = clientByName.updated(clientName, client))
    }

    def withService(name: String, service: Service) = copy(serviceByName.updated(name, service))
  }

}
