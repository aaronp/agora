package agora.api.io

import agora.api.BaseSpec

class LowPriorityIOImplicitsTest extends BaseSpec with LowPriorityIOImplicits {
  "RichPath.nestedFiles" should {
    "list all nested files" in {
      withDir { dir =>
        dir.resolve("a").resolve("b").resolve("c").text = "see"
        dir.resolve("x").resolve("y").resolve("z").text = "zee"
        dir.resolve("x").resolve("file.txt").text = "file"
        dir.resolve("foo").text = "bar"

        dir.nestedFiles.map(_.fileName).toList should contain only("c", "z", "file.txt", "foo")

      }
    }
  }
  "RichPath.linkToFrom" should {
    "create symbolic links" in {
      withDir { dir =>
        val file = dir.resolve("file.txt").text = "Some file data"
        val link = file.linkToFrom(dir.resolve("file.link"))
        link.text shouldBe "Some file data"
      }
    }
    "keep the source file when the link is deleted" in {
      withDir { dir =>
        val file = dir.resolve("file.txt").text = "Some file data"
        val link = file.linkToFrom(dir.resolve("file.link"))
        file.exists shouldBe true
        link.exists shouldBe true
        link.delete()
        file.exists shouldBe true
        link.exists shouldBe false
      }
    }
  }

}
