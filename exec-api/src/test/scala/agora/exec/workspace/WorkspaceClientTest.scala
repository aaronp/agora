package agora.exec.workspace

import java.nio.file.Path
import java.util.UUID

import agora.BaseSpec
import agora.exec.model.Upload
import agora.rest.HasMaterializer
import akka.actor.PoisonPill
import akka.stream.scaladsl.Source
import akka.util.ByteString

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class WorkspaceClientTest extends WorkspaceClientTCK {

  override def withWorkspaceClient[T](testWithClient: (WorkspaceClient, Path) => T): T = {

    withDir { containerDir =>
      val client = WorkspaceClient(containerDir, system, 100.millis)
      val result = testWithClient(client, containerDir)
      client.endpointActor ! PoisonPill
      result
    }
  }
}
