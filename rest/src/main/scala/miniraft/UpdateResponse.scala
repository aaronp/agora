package miniraft

import java.util.concurrent.atomic.AtomicInteger

import com.typesafe.scalalogging.StrictLogging
import miniraft.state.{LogIndex, NodeId, isMajority}

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.Success

/**
  * Provides a means to keep track of all the 'append entries' responses received for a given end-user (client)
  * request. The 'result' can be used to finally reply to the user request as it completes when a quorum of results
  * confirms (or denies) the success of the update
  */
trait UpdateResponse {

  /** @return the individual response futures */
  def acks: Map[NodeId, Future[AppendEntriesResponse]]

  /** @return the eventual insert result, either ok or otherwise when consensus is reached
    */
  def result: Future[Boolean]

}

object UpdateResponse {

  private[miniraft] def apply(nodes: Set[NodeId], logIndex: LogIndex)(implicit ec: ExecutionContext) = {
    Appendable(logIndex, nodes.map(_ -> Promise[AppendEntriesResponse]()).toMap)
  }

  def apply(reply: Future[Boolean], responses: Map[NodeId, Future[AppendEntriesResponse]] = Map.empty): UpdateResponse = {
    new Instance(responses, reply)
  }

  class Instance(override val acks: Map[NodeId, Future[AppendEntriesResponse]], override val result: Future[Boolean]) extends UpdateResponse

  /**
    * Provides a means to keep track of all the 'append entries' responses received for a given end-user (client)
    * request.
    *
    * @param logIndex the log index we're appending to
    * @param results  the received results
    * @param ec
    */
  private[miniraft] case class Appendable(logIndex: LogIndex, results: Map[String, Promise[AppendEntriesResponse]])(implicit ec: ExecutionContext)
      extends UpdateResponse
      with StrictLogging {

    def completed = {
      results.collect {
        case (id, promise) if promise.isCompleted => id
      }
    }

    def pending = {
      results.collect {
        case (id, promise) if !promise.isCompleted => id
      }
    }

    override def toString = {
      s"${completed.mkString(",")} received, ${pending.mkString(",")} pending for client append request to log index $logIndex"
    }

    /** @return true when done */
    def onResponse(from: NodeId, resp: AppendEntriesResponse) = {
      // is this the ack for the logIndex append we sent?
      // NOTE : we *could* do a comparison and allow matchIndices > logIndex, as
      // we can then just assume ours is ok
      if (resp.matchIndex == logIndex) {
        val completedOpt = results.get(from).map { promise =>
          logger.info(s"Trying to complete client result for $from w/ $resp, given promise.isCompleted=${promise.isCompleted}")
          promise.trySuccess(resp)
          true
        }
        completedOpt.getOrElse(false)
      } else {
        logger.info(s"Ignoring append entries ack from unknown client '$from'")
        false
      }
    }

    override def acks: Map[NodeId, Future[AppendEntriesResponse]] = results.mapValues(_.future)

    private val completePromise   = Promise[Boolean]()
    private val okCounter         = new AtomicInteger(0)
    private lazy val notOkCounter = new AtomicInteger(0)
    private val total             = results.size
    results.values.foreach { p =>
      p.future.onComplete {
        case Success(ack) if ack.success =>
          if (isMajority(okCounter.incrementAndGet(), total)) {
            completePromise.trySuccess(true)
          }
        case _ =>
          if (isMajority(notOkCounter.incrementAndGet(), total)) {
            completePromise.trySuccess(false)
          }
      }
    }

    override def result: Future[Boolean] = completePromise.future
  }

}
