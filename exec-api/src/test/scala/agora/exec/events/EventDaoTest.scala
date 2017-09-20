package agora.exec.events

import agora.BaseSpec
import agora.exec.model.RunProcess

import scala.util.Success

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
    "find jobs which have been received but not started in the time range" in {
      withDao { dao =>
        val job = ReceivedJob("a", None, RunProcess(Nil))
        dao.receivedDao.save(job, job.received)

        val started = StartedJob(job.id, job.received.plusMinutes(1))
        dao.startedDao.save(started, started.started)

        dao.notStartedBetween(job.received, started.started.minusNanos(1)).toList should contain(job)
        dao.notStartedBetween(job.received, started.started).toList shouldBe empty
        dao.notStartedBetween(job.received.plusNanos(1), started.started.minusNanos(1)).toList shouldBe empty
      }
    }
  }
  "EventDao.notFinishedBetween" should {
    "find jobs which have been started but not completed within the time range" in {
      withDao { dao =>
        val job = ReceivedJob("a", None, RunProcess(Nil))
        dao.receivedDao.save(job, job.received)

        val started = StartedJob(job.id, job.received.plusMinutes(1))
        dao.startedDao.save(started, started.started)

        val finished = CompletedJob(job.id, Success(1), job.received.plusMinutes(2))
        dao.completedDao.save(finished, finished.completed)

        dao.notFinishedBetween(started.started, finished.completed.minusNanos(1)).toList should contain(started)
        dao.notFinishedBetween(started.started, finished.completed).toList shouldBe empty
        dao.notFinishedBetween(started.started.plusNanos(1), finished.completed.minusNanos(1)).toList shouldBe empty
      }
    }
  }

  def withDao[T](f: EventDao => T): T = withDir { dir =>
    f(EventDao(dir))
  }
}
