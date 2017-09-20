package agora.exec.events

import agora.BaseSpec
import agora.exec.model.RunProcess
import agora.rest.HasMaterializer

trait SystemEventMonitorSpec extends BaseSpec with HasMaterializer {

  "monitor.accept(ReceiveJob)" should {
    "be able to retrieve saved job" in {
      withDao { dao =>
        val job = ReceivedJob("a", None, RunProcess(Nil))

//        dao.query(ReceivedBetween())
//
//        dao.accept("a") shouldBe None
//        dao.saveReceived(job)
//        dao.findJob("a") shouldBe Option(job)
//
//        dao.receivedBetween(job.received, job.received).toList should contain only (job)
//        dao.receivedBetween(job.received.minusNanos(2), job.received.minusNanos(1)).toList shouldBe empty
//        dao.receivedBetween(job.received.plusNanos(1), job.received.plusNanos(2)).toList shouldBe empty
//
//
//        dao.startedBetween(job.received, job.received).toList shouldBe empty
//        dao.completedBetween(job.received, job.received).toList shouldBe empty
      }
    }
  }


  def withDao[T](f: EventDao => T): T
}