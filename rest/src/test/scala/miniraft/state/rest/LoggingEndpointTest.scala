package miniraft.state.rest

import agora.BaseRestSpec
import org.scalatest.FunSuite

class LoggingEndpointTest extends BaseRestSpec {

  "LoggingEndpoint.removeFilesWithAnIntegerPrefixPriorToN" should {
    "delete indexed files which begin with '<id>-' where <id> is less than the provided integer" in {
      withDir { dir =>
        dir.resolve("1-one.txt").text = "one"
        dir.resolve("2-two.txt").text = "two"
        dir.resolve("3-but-i-am-a-dir").mkDirs()
        dir.resolve("three.txt").text = "three"
        dir.resolve("four.txt").text = "four"
        dir.resolve("5-five.log").text = "five"

        dir.children.size shouldBe 6
        LoggingEndpoint.removeFilesWithAnIntegerPrefixPriorToN(dir, 2)

        // remove 1-one.txt
        dir.children.size shouldBe 5
        dir.children.map(_.fileName) should not contain ("1-one.txt")
        dir.children.map(_.fileName) should contain("2-two.txt")

        // remove before 5, but that should exclude the dir

        LoggingEndpoint.removeFilesWithAnIntegerPrefixPriorToN(dir, 5)
        dir.children.size shouldBe 4
        dir.children.map(_.fileName) should not contain ("1-one.txt")
        dir.children.map(_.fileName) should contain only ("3-but-i-am-a-dir", "5-five.log", "three.txt", "four.txt")
      }
    }
  }
}
