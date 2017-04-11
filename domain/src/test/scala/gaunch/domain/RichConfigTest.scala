package jabroni.domain

import java.nio.file.Files

import com.typesafe.config.ConfigRenderOptions
import org.scalatest.{Matchers, WordSpec}

import scala.compat.Platform.EOL
import scala.sys.SystemProperties
import scala.util.Properties

class RichConfigTest extends WordSpec with Matchers {

  "RichConfig.ParseArg.AsBooleanFlag" should {
    "treat text as a boolean flag" in {
      val key = "this/is``\\a-string"
      val conf = RichConfig.ParseArg.AsBooleanFlag(key)
      import RichConfig.implicits._
      conf.getBoolean(key.quoted) shouldBe true
    }
  }
  "RichConfig.asConfig" should {

    "convert a key value pair to a config" in {
      val c = RichConfig.asConfig("hi", "there")
      val confStr = c.root.render(ConfigRenderOptions.concise())
      confStr shouldBe "{\"hi\":\"there\"}"
    }
  }

  "RichConfig.implicits" should {
    import RichConfig.implicits._

    "convert user args to a config" in {

      val sp = new SystemProperties
      val original = sp.get("config.file")
      val someConfFile = Files.createTempFile("RichConfigTest", ".conf")
      try {
        sp += ("config.file" -> someConfFile.toString)
        val value = System.currentTimeMillis()
        Files.write(someConfFile, s"testValue=${value}${EOL}foo=bar".getBytes)

        // override the value in testValue
        val conf = Array("testValue=123", "flag=true").asConfig()
        conf.getBoolean("flag") shouldBe true
        conf.getString("foo") shouldBe "bar"
        conf.getLong("testValue") shouldBe 123L

        // don't override the value in testValue
        val conf2 = Array("flag=true").asConfig()
        conf2.getBoolean("flag") shouldBe true
        conf2.getString("foo") shouldBe "bar"
        conf2.getLong("testValue") shouldBe value

      } finally {
        original match {
          case None => sp - "config.file"
          case Some(value) => sp += ("config.file" -> value)
        }
        Files.delete(someConfFile)
      }
    }
    "convert a file path as a user arg to a config" in {
      val someConfFile = Files.createTempFile("RichConfigTest", ".conf")
      try {
        val value = System.currentTimeMillis()
        Files.write(someConfFile, s"testValue=${value}${EOL}foo=bar".getBytes)

        // override the value in testValue
        val conf = Array(someConfFile.toString).asConfig()
        conf.getString("foo") shouldBe "bar"
        conf.getLong("testValue") shouldBe value
        conf.getString("user.name") shouldBe Properties.userName
      } finally {
        Files.delete(someConfFile)
      }
    }
  }

}
