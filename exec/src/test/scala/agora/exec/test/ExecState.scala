package agora.exec.test

import java.io.Closeable

import agora.BaseSpec
import agora.exec.ExecConfig
import agora.exec.client.RemoteRunner
import agora.exec.model.{RunProcess, RunProcessResult, StreamingResult}
import agora.exec.rest.ExecutionRoutes
import agora.rest.RunningService
import miniraft.state.NodeId
import org.scalatest.concurrent.Eventually

import scala.concurrent.Future

object ExecState {

  type Service = RunningService[ExecConfig, ExecutionRoutes]
}

case class ExecState(serviceByName: Map[String, ExecState.Service] = Map.empty,
                     clientsByName: Map[String, (ExecConfig, RemoteRunner)] = Map.empty,
                     resultsByClient: Map[String, Future[RunProcessResult]] = Map.empty)
    extends BaseSpec
    with Eventually {

  def executeRunProcess(clientName: String, jobId: String, proc: RunProcess): ExecState = {

    resultsByClient.contains(clientName) shouldBe false

    val client = clientsByName
      .getOrElse(clientName, sys.error(s"Couldn't find client '$clientName' in ${clientsByName.keySet}"))
      ._2
    val future = client.run(proc)
    copy(resultsByClient = resultsByClient.updated(clientName, future))
  }

  def stopClient(nodeId: NodeId) = {
    val (conf, r) = clientsByName(nodeId)
    r match {
      case c: Closeable =>
        c.close()
      case _ =>
    }
    conf.clientConfig.restClient.close

    this
  }

  def close() = {
    serviceByName.values.foreach(_.close())
    clientsByName.values.foreach {
      case (_, c: Closeable) => c.close()
      case _                 =>
    }
    new ExecState()
  }

  def verifyExecResult(expectedOutput: String) = {
    resultsByClient.toList match {
      case List((client, future)) =>
        val StreamingResult(streamingOut) = future.futureValue
        streamingOut.mkString("\n") shouldBe expectedOutput

        copy(resultsByClient = resultsByClient - client)
      case many =>
        fail(s"Expected a single result, but found $many")
        this
    }
  }

  def execute(clientName: String, command: String): ExecState = {
    val commands: List[String] = command.split(" ", -1).toList
    executeRunProcess(clientName, "unspecified job id", RunProcess(commands))
  }

  def connectClient(name: String, port: Int) = {
    clientsByName.keySet should not contain (name)
    val conf: ExecConfig        = ExecConfig(s"port=$port", s"actorSystemName=$name")
    val newClient: RemoteRunner = conf.remoteRunner

    copy(clientsByName = clientsByName.updated(name, conf -> newClient))
  }

  def connectClient(clientName: String, serverName: String) = {
    val clientConf: ExecConfig = serviceByName(serverName).conf
    val client: RemoteRunner   = clientConf.remoteRunner()
    copy(clientsByName = clientsByName.updated(clientName, (clientConf, client)))
  }

  def startExecutorOnPort(port: Int) = {
    val conf = ExecConfig(s"port=$port", "actorSystemName=exec-test-server")
    withService(s"Service on $port", conf.start().futureValue)
  }

  def withService(name: String, service: ExecState.Service) = {
    require(!serviceByName.contains(name), s"There is already a service $name")
    copy(serviceByName.updated(name, service))
  }

}
