package jabroni.exec

import com.typesafe.config.ConfigFactory
import jabroni.rest.BaseSpec

import scala.concurrent.duration._

class ExecConfigTest extends BaseSpec {

  "ExecConfig.defaultConfig" should {
    "contain uploadTimeout" in {


      val ec = ExecConfig().add(ConfigFactory.parseString(
        """workingDirectory.dir=wd
          |workingDirectory.appendJobId = false
          |logs.dir=logs
          |uploads.dir=uploads
        """.stripMargin))


      ec.uploadTimeout shouldBe 10.seconds
      ec.workingDirectory.appendJobId shouldBe false
      ec.workingDirectory.dir("foo").map(_.toAbsolutePath) shouldBe Some("wd".asPath.toAbsolutePath)

      ec.uploads.dir("xyz").map(_.toAbsolutePath) shouldBe Option("uploads/xyz".asPath.toAbsolutePath)
      ec.logs.dir("abc").map(_.toAbsolutePath) shouldBe Option("logs/abc".asPath.toAbsolutePath)
    }
  }
}
