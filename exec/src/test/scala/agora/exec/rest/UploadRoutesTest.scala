package agora.exec.rest

import agora.exec.client.UploadClient
import agora.exec.workspace.WorkspaceClient
import agora.rest.BaseRoutesSpec
import akka.http.scaladsl.model.ContentTypes._
import akka.http.scaladsl.server.Route
import akka.stream.scaladsl.Source
import akka.util.ByteString
import concurrent.duration._

class UploadRoutesTest extends BaseRoutesSpec {
  "UploadRoutes" should {
    "accept uploads from the upload client" in {
      withDir { dir =>
        val workspaceClient = WorkspaceClient(dir, system, 100.millis)

        // we're really testing the route and the client to the route in tandem
        val routesUnderTest: Route = UploadRoutes(workspaceClient).uploadRoute

        dir.children shouldBe empty

        val contentBytes = ByteString("hello world")
        val req = UploadClient
          .asRequest("some workspace", "foo.bar", contentBytes.length, Source.single(contentBytes), `text/plain(UTF-8)`)
          .futureValue

        req ~> routesUnderTest ~> check {
          responseAs[Boolean] shouldBe true
        }

        dir.children.toList.map(_.fileName) should contain only ("some workspace")
        dir.children.head.children.toList.map(_.fileName) should contain only ("foo.bar", ".foo.bar.metadata")
        dir.children.head.resolve("foo.bar").text shouldBe "hello world"

        val workspaceDir = workspaceClient.awaitWorkspace("some workspace", Set("foo.bar"), testTimeout.toMillis).futureValue
        workspaceDir shouldBe dir.children.head
      }
    }
  }

}
