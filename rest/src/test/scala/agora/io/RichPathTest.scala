package agora.io

import agora.BaseSpec

class RichPathTest extends BaseSpec with LowPriorityIOImplicits {

  "foo.createdString" should {
    "return the created file date as a string" in {
      val foo = "target/foo".asPath.createIfNotExists()
      try {
        // can't wait for someone to fix this in year 2100! mwa ha ha!
        foo.createdString should startWith("20")
      } finally {
        foo.delete()
        foo.exists shouldBe false
      }
    }
  }
  "path.search" should {
    "find files under the given path" in {
      withDir { dir =>
        dir.resolve("a/b/c/d/foundMe.txt").text = "under a/b/c/d"
        dir.resolve("a/b/foundMe.too").text = "under a/b"
        dir.resolve("a/b/c/i.dont.match").text = "nope"

        val found = dir.find(_.fileName startsWith "foundMe").toList

        found.map(_.text) should contain only ("under a/b/c/d", "under a/b")

        // belt and braces -- just verify our '.text' isn't broken, and we actually
        // created files under nested dirs

        found.head.parents.map(_.fileName) should contain("b")
        found.head.parents.map(_.fileName) should contain("a")

      }
    }
  }
}
