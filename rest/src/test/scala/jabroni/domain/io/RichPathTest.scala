package jabroni.domain.io

import org.scalatest.{Matchers, WordSpec}

class RichPathTest extends WordSpec with Matchers with LowPriorityIOImplicits {

  "foo.createdString" should {
    "return the created file date as a string" in {
      val foo = "foo".asPath.createIfNotExists()
      try {
        // can't wait for someone to fix this in year 2100! mwa ha ha!
        foo.createdString should startWith ("20")
      } finally {
        foo.delete()
      }
    }
  }
}
