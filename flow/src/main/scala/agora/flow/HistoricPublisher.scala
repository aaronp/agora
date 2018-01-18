package agora.flow

import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}

import com.typesafe.scalalogging.StrictLogging
import org.reactivestreams.{Publisher, Subscriber, Subscription}

import scala.concurrent.ExecutionContext
import scala.util.{Failure, Success, Try}

/**
  * processor which lets you subscribe to values from a particular index
  *
  * @tparam T
  */
trait HistoricProcessor[T] extends Publisher[T] with Subscriber[T] {

  def subscribeFrom(index: Long, subscriber: Subscriber[_ >: T]): Unit

  override def subscribe(subscriber: Subscriber[_ >: T]) = subscribeFrom(firstIndex, subscriber)

  def firstIndex: Long

  def latestIndex: Option[Long]
}

object HistoricProcessor extends StrictLogging {

  def apply[T](dao: HistoricProcessorDao[T], previouslyRequestedIndex: Long = -1L)(implicit ec: ExecutionContext) = {
    new Instance[T](dao, new AtomicLong(previouslyRequestedIndex))
  }

  private[flow] def computeNumberToTake(lastReceivedIndex: Long, latest: Long, maxIndex: Long): Long = {
    val nrToTake = {
      val maxAvailable = maxIndex.min(latest)
      val nr           = (maxAvailable - lastReceivedIndex).max(0)
      logger.debug(s"""
           |Will try to pull $nr :
           |              last received index : $lastReceivedIndex
           |  max index of published elements : $latest
           |  currently requested up to index : $maxIndex
           |                 limit to pull to : $maxAvailable
             """.stripMargin)
      nr
    }
    nrToTake
  }

  class Instance[T](dao: HistoricProcessorDao[T], currentIndexCounter: AtomicLong) extends HistoricProcessor[T] {
    def snapshot(): PublisherSnapshot[Int] = {
      val map = subscribers.zipWithIndex.map(_.swap).toMap
      PublisherSnapshot(map.mapValues(_.snapshot()))
    }

    private val initialIndex: Long = currentIndexCounter.get()

    private var subscribers     = List[HistoricSubscriber]()
    private var subscriptionOpt = Option.empty[Subscription]

    private object SubscribersLock

    private[flow] class HistoricSubscriber(initialRequestedIndex: Long, val subscriber: Subscriber[_ >: T]) extends Subscription {
      private[flow] val nextIndexToRequest = new AtomicLong(initialRequestedIndex)
      private[flow] val lastRequestedIndex = new AtomicLong(initialRequestedIndex)
      private val totalPushed              = new AtomicInteger(0)

      def snapshot() = {
        val lastRequested = lastRequestedIndex.get
        val next          = nextIndexToRequest.get
        SubscriberSnapshot(-1, totalPushed.get, lastRequested.toInt, next - lastRequested, 0, ConsumerQueue.Unbounded)
      }

      def onNewIndex(newIndex: Long) = {
        if (newIndex <= nextIndexToRequest.get()) {
          pull(newIndex)
        }
      }

      def complete() = Try(subscriber.onComplete())

      private def deadIndex = initialIndex - 1

      override def cancel(): Unit = {
        if (nextIndexToRequest.getAndSet(deadIndex) != deadIndex) {
          SubscribersLock.synchronized {
            subscribers = subscribers.diff(List(this))
          }
        }
      }

      private def pullNext(remainingToTake: Long): Unit = {
        if (remainingToTake > 0) {
          val idx         = lastRequestedIndex.incrementAndGet()
          implicit val ec = dao.executionContext

          val future = dao.at(idx)
          logger.debug(s"Requesting index $idx")
          future.onComplete {
            case Failure(err) =>
              cancel()
              subscriber.onError(err)
            case Success(elm) =>
              totalPushed.incrementAndGet()
              subscriber.onNext(elm)
              pullNext(remainingToTake - 1)
          }
        }
      }

      private def pull(maxIndex: Long): Unit = {
        val nrToTake = computeNumberToTake(lastRequestedIndex.get(), currentIndex(), maxIndex)

        pullNext(nrToTake)
      }

      override def request(n: Long): Unit = {
        pull(nextIndexToRequest.addAndGet(n))
      }
    }

    override def subscribeFrom(index: Long, subscriber: Subscriber[_ >: T]): Unit = {
      val hs = SubscribersLock.synchronized {
        // we start off not having requested anything, so start 1 BEFORE the index
        val lastRequestedIndex = index - 1
        val newSubscriber      = new HistoricSubscriber(lastRequestedIndex, subscriber)
        subscribers = newSubscriber :: subscribers
        newSubscriber
      }
      hs.subscriber.onSubscribe(hs)
    }

    private def clearSubscribers() = {
      SubscribersLock.synchronized {
        subscribers = Nil
      }
    }

    private def currentIndex() = currentIndexCounter.get()

    override def latestIndex: Option[Long] = Option(currentIndex()).filterNot(_ == initialIndex)

    override val firstIndex = initialIndex + 1

    override def onNext(value: T): Unit = {
      val newIndex = currentIndexCounter.incrementAndGet()
      dao.writeDown(newIndex, value)
      foreachSubscriber(_.onNewIndex(newIndex))
    }

    private def foreachSubscriber(f: HistoricSubscriber => Unit) = {
      subscribers.size match {
        case 0 =>
        case 1 => subscribers.foreach(f)
        case _ => subscribers.par.foreach(f)
      }
    }

    override def onError(t: Throwable): Unit = {
      foreachSubscriber(_.subscriber.onError(t))
      clearSubscribers()
      subscriptionOpt = None
    }

    override def onComplete(): Unit = {
      foreachSubscriber(_.complete())
      clearSubscribers()
      subscriptionOpt = None
    }

    override def onSubscribe(s: Subscription): Unit = {
      require(subscriptionOpt.isEmpty, "Already subscribed")
      subscriptionOpt = Option(s)
    }
  }

}
