package jabroni.exchange

import java.nio.file.{Files, Path, StandardOpenOption}

import jabroni.api.JobId
import jabroni.api.client.{ClientRequest, SubmitJob}
import jabroni.api.exchange.SelectionMode
import jabroni.api.exchange.SelectionMode.Selected
import jabroni.api.worker.{RequestWork, WorkerRequest}

trait WorkDispatcher {

  def dispatch(selection: SelectionMode.Selected, jobId: JobId, job: SubmitJob): Unit

}

object WorkDispatcher {

  /**
    * Remembers where things were sent
    *
    * @param underlying
    */
  case class PersistentWorkDispatcher(jobDir: Path, workerDir: Path, underlying: WorkDispatcher) extends WorkDispatcher {

    import scala.collection.JavaConverters._

    def writeDown(selection: SelectionMode.Selected, jobId: JobId, job: SubmitJob) = {
      saveJob(jobId, job, selection)
      saveWork(jobId, job, selection)
    }

    def saveWork(jobId: JobId, job: SubmitJob, selection: SelectionMode.Selected) = {
      selection.foreach {
        case (workId, offer: WorkerRequest) =>
          val workRequestDir = Files.createDirectory(workerDir.resolve(workId.toString))
          saveUnder(workRequestDir.resolve(s"$jobId.json"), offer.json.noSpaces)
      }
    }

    def saveJob(jobId: JobId, job: SubmitJob, selection: SelectionMode.Selected) = {
      val saveJobToFile = Files.createDirectory(jobDir.resolve(jobId.toString))
      saveUnder(saveJobToFile.resolve("job.json"), job.json.noSpaces)
      val offersDir = Files.createDirectory(saveJobToFile.resolve("offers"))
      selection.foreach {
        case (workId, offer: WorkerRequest) => saveUnder(offersDir.resolve(s"$workId.json"), offer.json.noSpaces)
      }
    }

    private def saveUnder(file: Path, content: String) = {
      Files.write(file, Iterable(content).asJava, StandardOpenOption.CREATE, StandardOpenOption.SYNC)
    }

    override def dispatch(selection: SelectionMode.Selected, jobId: JobId, job: SubmitJob): Unit = {
      writeDown(selection, jobId, job)
      underlying.dispatch(selection, jobId, job)
    }
  }

  def apply(): WorkDispatcher = {

  }
}
