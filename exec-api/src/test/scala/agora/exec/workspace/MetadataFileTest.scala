package agora.exec.workspace

import java.nio.file.Path

import agora.BaseExecApiSpec

class MetadataFileTest extends BaseExecApiSpec {
  "MetadataFile.isMetadataFile" should {
    List(
      ".some.file.metadata"         -> true,
      ".someFile.metadata"          -> true,
      ".some.filemetadata"          -> false,
      "some.file.metadata"          -> false,
      "some.file.metadata.contents" -> false,
      ".some.file.Metadata"         -> false
    ).foreach {
      case (name, expected) =>
        s"return $expected for $name" in {
          MetadataFile.isMetadataFile(name) shouldBe expected
        }
    }
  }
  "MetadataFile.readyFiles" should {
    "return the files which contain metadata files" in {
      withDir { testDir =>
        val file1 = testDir.resolve("test1").text = "hi"
        MetadataFile.metadataFileForUpload(file1).toList.size shouldBe 1
        MetadataFile.metadataFileForUpload(file1).toList.filter(_.exists()) shouldBe empty
        val file2 = testDir.resolve("test2").text = "there"
        MetadataFile.metadataFileForUpload(file2).toList.filter(_.exists()) shouldBe empty

        MetadataFile.createMetadataFileFor(file1).isDefined shouldBe true

        MetadataFile.readyFiles(testDir, true).map(_.fileName).toList shouldBe List("test1")
      }
    }
  }
  "MetadataFile.metadataFileForUpload" should {
    "return the metadata file for the given file, if one exists" in {
      withDir { testDir =>
        val file1: Path = testDir.resolve("test1").text = "hi"
        MetadataFile.metadataFileForUpload(file1).toList.filter(_.exists()) shouldBe empty
        val file2 = testDir.resolve("test2").text = "there"
        MetadataFile.metadataFileForUpload(file2).toList.filter(_.exists()) shouldBe empty

        def abs(file: Option[Path]) = {
          file.toList.filter(_.exists()).map(_.toAbsolutePath.toString)
        }

        def verify(file: Option[Path], mdFile: Option[Path]) = abs(file) shouldBe abs(mdFile)

        val mdFile1 = MetadataFile.createMetadataFileFor(file1)
        verify(mdFile1, MetadataFile.metadataFileForUpload(file1))

        val mdFile2 = MetadataFile.createMetadataFileFor(file2)
        verify(mdFile2, MetadataFile.metadataFileForUpload(file2))
      }
    }
  }
  "MetadataFile.createMetadataFileFor" should {
    "not create a metadata file if the target file doesn't exist" in {
      withDir { testDir =>
        val file = testDir.resolve("doesntExist")
        MetadataFile.createMetadataFileFor(file) shouldBe None
        testDir.children.size shouldBe 0
      }
    }
    "create a metadata file next to the existing file" in {
      withDir { testDir =>
        val testContent =
          """These are
            |the context of some file.
            |It could be the output of some job
          """.stripMargin
        val file = testDir.resolve("some file").text = testContent

        withClue("precondition check - validate the file size") {
          file.size should be > 0L
          file.size shouldBe testContent.getBytes.size
        }

        // call the method under test
        val mdFile = MetadataFile.createMetadataFileFor(file)

        testDir.children.size shouldBe 2
        testDir.resolve(".some file.metadata").isFile shouldBe true

        mdFile shouldBe Some(testDir.resolve(".some file.metadata"))
        mdFile.map(_.text).get shouldBe file.size.toString
      }

    }
  }
}
