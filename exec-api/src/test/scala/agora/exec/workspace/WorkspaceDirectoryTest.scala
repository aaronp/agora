package agora.exec.workspace

import agora.BaseSpec
import agora.rest.HasMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString

import scala.concurrent.Promise

class WorkspaceDirectoryTest extends BaseSpec with HasMaterializer {
  "WorkspaceDirectory.onUpload" should {
    "create a corresponding metadata file" in {
      withDir { dir =>
        val content = ByteString("content")

        val wd = WorkspaceDirectory(dir)

        // call the method under test
        val future       = wd.onUpload(UploadFile(dir.fileName, "some.name", Source.single(content), Promise()))
        val (size, path) = future.futureValue
        size shouldBe content.size
        path.fileName shouldBe "some.name"

        wd.eligibleFiles(true) should contain only ("some.name")
      }
    }
  }
}
