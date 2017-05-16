package jabroni.exec

import jabroni.rest.BaseSpec

import scala.concurrent.duration._

class ExecConfigTest extends BaseSpec {

  "ExecConfig.defaultConfig" should {
    "contain uploadTimeout" in {

      import jabroni.domain.io.implicits._

      val ec = ExecConfig(Array.empty)
      ec.uploadTimeout shouldBe 10.seconds
      ec.baseWorkDir.map(_.toAbsolutePath) shouldBe Some("target/exec-test/work".asPath.toAbsolutePath)
      ec.baseUploadDir.toAbsolutePath shouldBe "target/exec-test/work".asPath.toAbsolutePath
      ec.baseLogDir.map(_.toAbsolutePath) shouldBe Option("target/exec-test/logs".asPath.toAbsolutePath)
    }
  }
}
