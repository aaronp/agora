package jabroni.exec

import com.typesafe.config.ConfigFactory
import jabroni.rest.BaseSpec

import scala.concurrent.duration._

class ExecConfigTest extends BaseSpec {

  "ExecConfig.defaultConfig" should {
    "contain uploadTimeout" in {

      import jabroni.domain.io.implicits._

      val ec = ExecConfig(ConfigFactory.parseString(
        """exec.workingDirectory.dir=wd
          |exec.logs.dir=logs
          |exec.uploads.dir=uploads
        """.stripMargin).withFallback(ExecConfig.defaultConfig))
      ec.uploadTimeout shouldBe 10.seconds
      ec.workingDirectory.appendJobId shouldBe false
      ec.workingDirectory.dir("foo").map(_.toAbsolutePath) shouldBe Some("wd".asPath.toAbsolutePath)

      ec.uploads.dir("xyz").map(_.toAbsolutePath) shouldBe Option("uploads/xyz".asPath.toAbsolutePath)
      ec.logs.dir("abc").map(_.toAbsolutePath) shouldBe Option("logs/abc".asPath.toAbsolutePath)
    }
  }
}
