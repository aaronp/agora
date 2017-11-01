package agora.api.exchange.observer

import java.util.concurrent.atomic.AtomicInteger

import agora.api.exchange._
import agora.api.worker.WorkerRedirectCoords
import agora.api.{epochUTC, nextMatchId}
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

class ExchangeObserverDelegate(initialObservers: List[ExchangeObserver] = Nil) extends ExchangeObserver with StrictLogging {

  import ExchangeObserverDelegate._

  private var observers = initialObservers

  override def onEvent(event: ExchangeNotificationMessage): Unit = {
    observers.foreach { obs =>
      try {
        obs.onEvent(event)
      } catch {
        case NonFatal(e) =>
          logger.error(s"Observer threw $e on $event")
      }
    }
  }

  def -=(observer: ExchangeObserver): Boolean = {
    val before = observers.size
    observers = observers.diff(List(observer))
    before != observers.size
  }

  def +=[O <: ExchangeObserver](observer: O): O = {
    observers = observer :: observers
    observer
  }

  /**
    * Appends a match observer which will trigger when it sees a match with the given job
    *
    * @return a future match
    */
  def awaitJob(job: SubmitJob)(implicit ec: ExecutionContext): Future[BlockingSubmitJobResponse] = {
    val promise = Promise[BlockingSubmitJobResponse]()

    onceWhen {
      case OnMatch(_, _, `job`, workers) =>
        val idTry = job.jobId match {
          case Some(id) => Success(id)
          case None     => Failure(new Exception(s"no job id was set on $job"))
        }
        val coordsAndDetails = workers.map {
          case Candidate(key, workSubscription, remaining) =>
            val d = workSubscription.details
            val c = WorkerRedirectCoords(workSubscription.details.location, key, remaining)
            (c, d)
        }
        val (coords, details) = coordsAndDetails.unzip

        val respFuture = idTry.map { id =>
          BlockingSubmitJobResponse(nextMatchId(), id, epochUTC, coords.toList, details.toList)
        }
        promise.complete(respFuture)
        ()
    }
    promise.future
  }

  /**
    * Invoke the partial function when it applies, then remove it
    */
  def onceWhen(pf: PartialFunction[ExchangeNotificationMessage, Unit]): PartialHandler =
    +=(new PartialHandler(this, pf, true))

  /**
    * Always invoke the partial function whenever it applies
    */
  def alwaysWhen(pf: PartialFunction[ExchangeNotificationMessage, Unit]): PartialHandler =
    +=(new PartialHandler(this, pf, false))

}

object ExchangeObserverDelegate {

  private val idCounter = new AtomicInteger(0)

  /**
    * Creates a new ExchangeObserverDelegate
    * @param initialObservers
    * @return
    */
  def apply(initialObservers: List[ExchangeObserver] = Nil) = new ExchangeObserverDelegate(initialObservers)

  class PartialHandler(observer: ExchangeObserverDelegate, pf: PartialFunction[ExchangeNotificationMessage, Unit], removeAfterInvocation: Boolean)
      extends ExchangeObserver {
    private val id        = idCounter.incrementAndGet()
    override def hashCode = id
    override def equals(other: Any) = {
      other match {
        case ph: PartialHandler => id == ph.id
        case _                  => false
      }
    }
    override def onEvent(event: ExchangeNotificationMessage): Unit = {
      if (pf.isDefinedAt(event)) {
        pf(event)
        if (removeAfterInvocation) {
          remove()
          ()
        }
      }
    }

    def remove(): Boolean = observer -= this
  }
}
