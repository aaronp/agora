package agora.exec.workspace

import java.nio.file.Path

import akka.actor.PoisonPill

import scala.concurrent.duration._

class WorkspaceClientTest extends WorkspaceClientTCK {

  override def withWorkspaceClient[T](testWithClient: (WorkspaceClient, Path) => T): T = {

    withDir { containerDir =>
      val client = WorkspaceClient(containerDir, system, 100.millis, WorkspaceClient.defaultAttributes)
      val result = testWithClient(client, containerDir)
      client.endpointActor ! PoisonPill
      result
    }
  }
}
