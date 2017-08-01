package agora.exec

import com.typesafe.config.ConfigFactory
import agora.rest.BaseSpec

import scala.concurrent.duration._

class ExecConfigTest extends BaseSpec {

  "ExecConfig.defaultConfig" should {
    "contain uploadTimeout" in {

      val ec = ExecConfig().withOverrides(ConfigFactory.parseString("""workingDirectory.dir=wd
          |workingDirectory.appendJobId = false
          |logs.dir=logs
          |uploads.dir=uploads
        """.stripMargin))

      ec.uploadTimeout shouldBe 10.seconds

      ec.uploads.dir("xyz").map(_.toAbsolutePath) shouldBe Option("uploads/xyz".asPath.toAbsolutePath)
      ec.logs.dir("abc").map(_.toAbsolutePath) shouldBe Option("logs/abc".asPath.toAbsolutePath)
    }
  }
}
