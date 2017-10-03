package agora.exec.client

import agora.BaseSpec
import agora.exec.log.IterableLogger
import agora.exec.model.RunProcess

import scala.sys.process
import scala.concurrent.ExecutionContext.Implicits._

class AsJProcessTest extends BaseSpec {

  "AsJProcess.unapply" should {
    "be able to extract a jprocess from a scala one" in {
      val proc                   = RunProcess("pwd")
      val sproc: process.Process = LocalRunner().startProcess(proc, IterableLogger(proc)).get
      AsJProcess.unapply(sproc).isDefined shouldBe true
    }
  }
}
