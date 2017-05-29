package jabroni.exec.run

import java.nio.file.Path

import akka.stream.Materializer
import com.typesafe.scalalogging.StrictLogging
import io.circe.Encoder
import jabroni.domain.AlphaCounter
import jabroni.exec.dao.UploadDao
import jabroni.exec.model.{RunProcess, Upload}
import jabroni.exec.run.ProcessRunner.ProcessOutput
import io.circe.generic.auto._
import jabroni.domain.io.implicits._

/**
  * Runner which writes down all its requests -- presumably for some kind of test replay or debugging
  *
  */
class RequestLoggingRunner(underlying: ProcessRunner,
                           baseDir: Path,
                           nextId: BufferedIterator[String] = AlphaCounter.from(0))(implicit mat: Materializer)
  extends ProcessRunner
    with StrictLogging {
  override def run(proc: RunProcess, inputFiles: List[Upload]): ProcessOutput = {
    import mat._
    val id = nextId.next()
    val dir = baseDir.resolve(id).mkDirs()
    val dao = UploadDao(dir)
    dao.writeDown(inputFiles).flatMap { paths =>
      logger.info(s"Saved request w/ ${paths.size} uploads to $dir")
      dir.resolve("request.json").text = implicitly[Encoder[RunProcess]].apply(proc).spaces2
      underlying.run(proc, inputFiles)
    }
  }
}
