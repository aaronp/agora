package jabroni.api.worker
package execute

import java.net.URI
import java.nio.file.Path

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

trait Executor[JobId, JobType] {
  type Output
  type Error

  def location: URI

  def accept(id: JobId, job: JobType): Output

  // Option[RunDetails[Output, Error]]
  def statusOf(id: JobId, job: JobType): Future[Option[RunDetails[Output, Error]]]

}


object Executor {
  type Aux[Id, Job, O, E] = Executor[Id, Job] {
    type Output = O
    type Error = E
  }

  def hostLocation = {
    val host = java.net.InetAddress.getLocalHost.getHostAddress
    URI.create(host)
  }

  class Delegate[Id, Job](val underlying: Executor[Id, Job]) extends Executor[Id, Job] {
    override type Output = underlying.Output
    override type Error = underlying.Error

    override def location = underlying.location

    override def accept(id: Id, job: Job) = underlying.accept(id, job)

    override def statusOf(id: Id, job: Job) = underlying.statusOf(id, job)
  }


  class PersistentWorker[Id, Job, O](underlying: Aux[Id, Job, O, Throwable],
                                     dao: JobDao[Id, Job, O, Throwable])(implicit ec: ExecutionContext) extends Delegate[Id, Job](underlying) {

    override def accept(id: Id, job: Job): Output = {
      val started = RunDetails.start[O, Throwable]()
      writeDown(started, id, job)
      try {
        val output = super.accept(id, job)
        writeDown(started.withOutput("result", output.asInstanceOf[O]), id, job)
        output
      } catch {
        case NonFatal(bang) =>
          writeDown(started.withError("exception", bang), id, job)
          throw bang
      }
    }

    def writeDown(details: RunDetails[O, Throwable], id: Id, job: Job): Boolean = {
      dao.save(id, (job, details))
      //      Files.write(baseDir.resolve(id.toString), toBytes(output), CREATE, SYNC)
      true
    }

    override def statusOf(id: Id, job: Job) = {
      dao.read(id).map { (foundOp: Option[(Job, RunDetails[O, Throwable])]) =>
        foundOp.collect {
          case (_, rd: RunDetails[Output, Throwable]) =>
            val gotcha = rd
            gotcha.asInstanceOf[RunDetails[O, Throwable]]
            ???
        }
      }
    }
  }

  class Instance[Id, Job, O, E](override val location: URI = hostLocation)(compute: Job => O) extends Executor[Id, Job] {
    override type Output = O
    override type Error = E

    override def accept(id: Id, job: Job): Output = {
      val result: O = compute(job)
      result.asInstanceOf[Output]
    }

    override def statusOf(id: Id, job: Job) = ???
  }


  def apply[Id, Job, O](compute: Job => O)(implicit location: URI = hostLocation): Aux[Id, Job, O, Throwable] = {
    new Instance(location)(compute)
  }
}