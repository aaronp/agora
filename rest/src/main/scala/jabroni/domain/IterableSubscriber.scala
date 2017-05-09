package jabroni.domain
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.{ArrayBlockingQueue, TimeUnit}

import org.reactivestreams.{Subscriber, Subscription}
import org.slf4j.LoggerFactory

class IterableSubscriber(bufferSize : Int = 10000) extends Subscriber[String] {
  private var subscriptionOpt = Option.empty[Subscription]
  private val complete = new AtomicBoolean(false)
  private val buffer = new ArrayBlockingQueue[String](bufferSize)
  private val logger = LoggerFactory.getLogger(getClass)
  @transient private var errorOpt = Option.empty[Throwable]

  override def onError(t: Throwable): Unit = {
    errorOpt = errorOpt.orElse(Option(t))
    logger.error(s"onError($t)")
    complete.compareAndSet(false, true)
    throw t
  }

  override def onComplete(): Unit = {
    logger.info(s"onComplete")
    complete.compareAndSet(false, true)
  }

  override def onNext(t: String): Unit = {
    logger.info(s"onNext($t)")
    buffer.put(t)
    subscription.request(1)
  }

  def subscription = subscriptionOpt.get

  override def onSubscribe(s: Subscription): Unit = {
    logger.info(s"onSubscribe(...)")
    require(subscriptionOpt.isEmpty)
    subscriptionOpt = Option(s)
    subscription.request(1)
  }

  def lines : Iterator[String] = iterator
  object iterator extends Iterator[String] {
    override def hasNext: Boolean = {
      errorOpt.foreach(throw _)
      nextOpt match {
        case Some(_) => true
        case None if isDone => false
        case None =>
          nextOpt = Option(buffer.poll(100, TimeUnit.MILLISECONDS))
          hasNext
      }
    }

    private var nextOpt: Option[String] = None

    def isDone = complete.get() && nextOpt.isEmpty

    override def next(): String = {
      require(hasNext)
      val value = nextOpt.getOrElse(throw new NoSuchElementException)
      nextOpt = None
      value
    }
  }

}