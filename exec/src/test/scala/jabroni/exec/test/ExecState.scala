package jabroni.exec.test

import jabroni.exec.ExecConfig
import jabroni.exec.rest.ExecutionRoutes
import jabroni.exec.run.ProcessRunner
import jabroni.exec.run.ProcessRunner.ProcessOutput
import jabroni.rest.{BaseSpec, RunningService}
import miniraft.state.NodeId

case class ExecState(
                      server: Option[RunningService[ExecConfig, ExecutionRoutes]] = None,
                      clientsByName: Map[String, (ExecConfig, ProcessRunner)] = Map.empty,
                      resultsByClient: Map[String, ProcessOutput] = Map.empty
                    ) extends BaseSpec {
  def stopClient(nodeId: NodeId) = {
    val conf = clientsByName(nodeId)._1
    conf.restClient
    this
  }

  def close() = {
    server.foreach(_.close())
    clientsByName.values.collect {
      case c: AutoCloseable => c.close()
    }
  }

  def verifyExecResult(expectedOutput: String) = {
    resultsByClient.toList match {
      case List((client, future)) =>
        future.futureValue.mkString("\n") shouldBe expectedOutput

        copy(resultsByClient = resultsByClient - client)
      case many =>
        fail(s"Expected a single result, but found $many")
        this
    }
  }

  def execute(clientName: String, command: String) = {
    resultsByClient.contains(clientName) shouldBe false

    val client = clientsByName(clientName)._2
    val commands: List[String] = command.split(" ", -1).toList
    val future: ProcessOutput = commands match {
      case Nil => client.run("")
      case head :: tail => client.run(head, tail: _*)
    }
    copy(resultsByClient = resultsByClient.updated(clientName, future))
  }

  def connectClient(name: String, port: Int) = {
    clientsByName.keySet should not contain (name)
    val conf: ExecConfig = ExecConfig(s"port=$port", s"actorSystemName=$name")
    val newClient: ProcessRunner = conf.remoteRunner()

    copy(clientsByName = clientsByName.updated(name, conf -> newClient))
  }

  def startExecutorOnPort(port: Int) = {
    val conf = ExecConfig(s"port=$port", "actorSystemName=exec-test-server")
    server.foreach(_.stop())
    copy(server = Option(conf.start().futureValue))
  }

}
