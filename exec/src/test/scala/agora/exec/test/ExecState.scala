package agora.exec.test

import java.io.Closeable

import agora.exec.ExecConfig
import agora.exec.model.{RunProcess, Upload}
import agora.exec.rest.ExecutionRoutes
import agora.exec.run.{ExecClient, ProcessRunner}
import agora.exec.run.ProcessRunner.ProcessOutput
import agora.rest.{BaseSpec, RunningService}
import miniraft.state.NodeId
import org.scalatest.concurrent.Eventually

case class ExecState(server: Option[RunningService[ExecConfig, ExecutionRoutes]] = None,
                     clientsByName: Map[String, (ExecConfig, ProcessRunner)] = Map.empty,
                     resultsByClient: Map[String, ProcessOutput] = Map.empty,
                     latestSearch: Option[(String, String)] = None)
    extends BaseSpec
    with Eventually {
  def verifyListingMetadata(expected: Map[String, List[String]]): ExecState = {
    execClient.listMetadata.futureValue shouldBe expected
    this
  }

  def execClient = {
    val (conf, _) = clientsByName.values.head
    ExecClient(conf.restClient)
  }

  def verifySearch(expectedResults: Set[String]) = {
    eventually {
      val found = execClient.findJobByMetadata(latestSearch.toMap.ensuring(_.nonEmpty)).futureValue
      found shouldBe expectedResults
    }
    copy(latestSearch = None)
  }

  def searchMetadata(key: String, value: String): ExecState = {
    latestSearch shouldBe empty
    copy(latestSearch = Option(key -> value))
  }

  def executeRunProcess(clientName: String, jobId: String, proc: RunProcess, uploads: List[Upload]): ExecState = {

    resultsByClient.contains(clientName) shouldBe false

    val client                = clientsByName(clientName)._2
    val future: ProcessOutput = client.run(proc, uploads)
    copy(resultsByClient = resultsByClient.updated(clientName, future))
  }
  def stopClient(nodeId: NodeId) = {
    val (conf, r) = clientsByName(nodeId)
    r match {
      case c: Closeable =>
        c.close()
      case _ =>
    }
    conf.restClient.close

    this
  }

  def close() = {
    server.foreach(_.close())
    clientsByName.values.foreach {
      case c: Closeable => c.close()
      case _            =>
    }
    new ExecState()
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
    val commands: List[String] = command.split(" ", -1).toList
    executeRunProcess(clientName, "unspecified job id", RunProcess(commands), Nil)
  }

  def connectClient(name: String, port: Int) = {
    clientsByName.keySet should not contain (name)
    val conf: ExecConfig         = ExecConfig(s"port=$port", s"actorSystemName=$name")
    val newClient: ProcessRunner = conf.remoteRunner()

    copy(clientsByName = clientsByName.updated(name, conf -> newClient))
  }

  def startExecutorOnPort(port: Int) = {
    val conf = ExecConfig(s"port=$port", "actorSystemName=exec-test-server")
    server.foreach(_.stop())
    copy(server = Option(conf.start().futureValue))
  }

}
