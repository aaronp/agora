package agora.exec.events

import agora.BaseExecApiSpec
import agora.exec.model.RunProcess
import agora.rest.HasMaterializer

class SystemEventMonitorTest extends BaseExecApiSpec with HasMaterializer {

  "SystemEventMonitor.query(FindJob)" should {
    "return received jobs" in {
      withDao { dao =>
        val job = ReceivedJob("a", None, RunProcess(Nil))

        dao.accept(job)

        val FindJobResponse(Some(found), None, None, None) = dao.query(FindJob("a")).futureValue

        found shouldBe job
      }
    }
  }

  def withDao[T](f: SystemEventMonitor => T): T = {
    withDir { dir =>
      val monitor: SystemEventMonitor = SystemEventMonitor(dir)(system)
      f(monitor)
    }
  }
}
