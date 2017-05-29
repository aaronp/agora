package jabroni.exec.run

import akka.stream.Materializer
import com.typesafe.scalalogging.StrictLogging
import jabroni.domain.AlphaCounter
import jabroni.exec.dao.ExecDao
import jabroni.exec.model.{RunProcess, Upload}
import jabroni.exec.run.ProcessRunner.ProcessOutput

/**
  * Runner which writes down all its requests -- presumably for some kind of test replay or debugging
  *
  */
class RequestLoggingRunner(underlying: ProcessRunner,
                           dao: ExecDao.FileExecDao,
                           nextId: BufferedIterator[String] = AlphaCounter.from(0))(implicit mat: Materializer)
  extends ProcessRunner
    with StrictLogging {

  override def run(inputProc: RunProcess, inputFiles: List[Upload]): ProcessOutput = {
    import mat._

    val id = nextId.next()
    dao.save(id, inputProc, inputFiles).flatMap { savedUnder =>
      val proc = inputProc.addMetadata("request.saved.dir" -> savedUnder.toAbsolutePath.toString, "input.job" -> id)
      underlying.run(proc, inputFiles)
    }
  }
}
