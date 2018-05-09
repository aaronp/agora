package lupin.pub.sequenced

import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.{ExecutionContext, Future}

/**
  * This is means to sever as an API between a publisher and its subscribers by sending 'SubscriberStateCommand'
  * messages
  */
trait ChildSubscriberApi {
  def send(cmd: SubscriberStateCommand): Future[SubscriberStateCommandResult]

  def onRequest(n: Long) = send(OnRequest(n))

  def onNewIndexAvailable(maxIndex: Long) = send(OnNewIndexAvailable(maxIndex))

  def onCancel() = send(OnCancel)

  def onComplete(maxIndex: Long) = send(OnComplete(maxIndex))

  def onError(err: Throwable) = send(OnError(err))

}

object ChildSubscriberApi {

  private class QueueBasedApi(commands: Queue, capacitySizeCheckLimit: Int) extends ChildSubscriberApi with StrictLogging {

    override def send(cmd: SubscriberStateCommand): Future[SubscriberStateCommandResult] = {
      logger.trace(s"enqueueing $cmd")
      enqueue(commands, cmd, capacitySizeCheckLimit)
    }
  }

  def apply[T](capacity: Int, conflateCommandQueueLimit: Option[Int] = None)(newRunnable: Queue => Runnable)(
      implicit execContext: ExecutionContext): ChildSubscriberApi = {
    val queue: Queue = newQ(capacity)

    val runnable = newRunnable(queue)
    execContext.execute(runnable)
    val capacitySizeCheckLimit = conflateCommandQueueLimit.getOrElse {
      (capacity * 0.7).toInt.min(100)
    }
    require(capacitySizeCheckLimit > 0, s"Invalid capacitySizeCheckLimit '$capacitySizeCheckLimit': we should conflate commands before they reach capacity")
    require(capacitySizeCheckLimit < capacity,
            s"Invalid capacitySizeCheckLimit '$capacitySizeCheckLimit': we should conflate commands before they reach capacity")
    new QueueBasedApi(queue, capacitySizeCheckLimit)
  }
}
