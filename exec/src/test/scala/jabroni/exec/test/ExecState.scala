package jabroni.exec.test

import java.io.Closeable

import jabroni.exec.ExecConfig
import jabroni.exec.model.{RunProcess, Upload}
import jabroni.exec.rest.ExecutionRoutes
import jabroni.exec.run.{ExecClient, ProcessRunner}
import jabroni.exec.run.ProcessRunner.ProcessOutput
import jabroni.rest.{BaseSpec, RunningService}
import miniraft.state.NodeId


case class RunJob(client: String, id: String, job: RunProcess, uploads: List[Upload], result: ProcessOutput)

case class ExecState(
                      server: Option[RunningService[ExecConfig, ExecutionRoutes]] = None,
                      clientsByName: Map[String, (ExecConfig, ProcessRunner)] = Map.empty,
                      resultsByClient: Map[String, ProcessOutput] = Map.empty,
                      jobs: List[RunJob] = Nil,
                      latestSearch: Option[(String, String)] = None
                    ) extends BaseSpec {
  def verifyListingMetadata(expected: Map[String, List[String]]): ExecState = {
    execClient.listMetadata.futureValue shouldBe expected
    this
  }

  def execClient = {
    val (conf, _) = clientsByName.values.head
    ExecClient(conf.restClient)
  }

  def verifySearch(expectedResults: Set[String]) = {
    val found = execClient.findJobByMetadata(latestSearch.toMap.ensuring(_.nonEmpty)).futureValue
    found shouldBe expectedResults
    copy(latestSearch = None)
  }

  def searchMetadata(key: String, value: String): ExecState = {
    latestSearch shouldBe empty
    copy(latestSearch = Option(key -> value))
  }

  def executeRunProcess(clientId: String, jobId: String, proc: RunProcess, uploads: List[Upload]): ExecState = {
    val (_, runner) = clientsByName(clientId)
    val result: ProcessOutput = runner.run(proc, uploads)
    val runJob = RunJob(clientId, jobId, proc, uploads, result)
    copy(jobs = runJob :: jobs)
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
      case c =>
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
