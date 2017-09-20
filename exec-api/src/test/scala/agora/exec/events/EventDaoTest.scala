package agora.exec.events

import agora.BaseSpec
import agora.exec.model.RunProcess

class EventDaoTest extends BaseSpec {

  "EventDao.receivedDao" should {
    "be able to retrieve received job" in {
      withDao { dao =>
        val job = ReceivedJob("a", None, RunProcess(Nil))

        dao.receivedDao.get("a") shouldBe None
        dao.receivedDao.save(job, job.received)
        dao.receivedDao.get("a") shouldBe Option(job)

        dao.receivedDao.findBetween(job.received, job.received).toList should contain only (job)
        dao.receivedDao.findBetween(job.received.minusNanos(2), job.received.minusNanos(1)).toList shouldBe empty
        dao.receivedDao.findBetween(job.received.plusNanos(1), job.received.plusNanos(2)).toList shouldBe empty


        // the other DAOs should have no effect
        dao.startedDao.findBetween(job.received, job.received).toList shouldBe empty
        dao.completedDao.findBetween(job.received, job.received).toList shouldBe empty
      }
    }
  }
  "EventDao.notStartedBetween" should {
    "be able to retrieve received job" in {
      withDao { dao =>
        val job = ReceivedJob("a", None, RunProcess(Nil))

        dao.receivedDao.get("a") shouldBe None
        dao.receivedDao.save(job, job.received)
        dao.receivedDao.get("a") shouldBe Option(job)

        dao.receivedDao.findBetween(job.received, job.received).toList should contain only (job)
        dao.receivedDao.findBetween(job.received.minusNanos(2), job.received.minusNanos(1)).toList shouldBe empty
        dao.receivedDao.findBetween(job.received.plusNanos(1), job.received.plusNanos(2)).toList shouldBe empty


        // the other DAOs should have no effect
        dao.startedDao.findBetween(job.received, job.received).toList shouldBe empty
        dao.completedDao.findBetween(job.received, job.received).toList shouldBe empty
      }
    }
  }


  def withDao[T](f: EventDao => T): T = withDir { dir =>
    f(EventDao(dir))
  }
}
