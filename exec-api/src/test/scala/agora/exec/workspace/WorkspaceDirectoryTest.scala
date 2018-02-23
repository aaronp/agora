package agora.exec.workspace

import java.nio.file.FileSystemException

import agora.BaseSpec
import agora.rest.HasMaterializer
import akka.stream.scaladsl.Source
import akka.util.ByteString

import scala.concurrent.{Await, Promise}
import scala.util.Try

class WorkspaceDirectoryTest extends BaseSpec with HasMaterializer {
  "WorkspaceDirectory.onUpload" should {
    "use a different temporary upload file if some already exist" in {
      withDir { dir =>
        val wd = WorkspaceDirectory(dir)

        val Some(coincidence1) = MetadataFile.newPartialUploadFileForUpload(dir.resolve("meh"))
        coincidence1.createIfNotExists().fileName shouldBe ".meh.partialupload"
        val Some(coincidence2) = MetadataFile.newPartialUploadFileForUpload(dir.resolve("meh"))
        coincidence2.createIfNotExists().fileName shouldBe ".meh.partialupload.1"
        val Some(coincidence3) = MetadataFile.newPartialUploadFileForUpload(dir.resolve("meh"))
        coincidence3.createIfNotExists().fileName shouldBe ".meh.partialupload.2"
        dir.children.size shouldBe 3

        val (size, uploadFile) = wd.onUpload(UploadFile(dir.fileName, "meh", Source.single(ByteString("content")), Promise()))(_ => {}).futureValue
        coincidence1.exists() shouldBe true
        coincidence2.exists() shouldBe true
        coincidence3.exists() shouldBe true
        uploadFile.exists() shouldBe true
        uploadFile.size shouldBe size
        uploadFile.text shouldBe "content"
      }

    }
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
