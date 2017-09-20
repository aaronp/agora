package agora.exec.events

import java.nio.file.Path

import agora.io.BaseActor
import akka.actor.{ActorRef, ActorRefFactory, Props}

import scala.concurrent.{Future, Promise}


/**
  * The event monitor is where we sent event notifications we care about (jobs started, stopped, failed, etc)
  */
trait SystemEventMonitor {
  def accept(event: RecordedEvent): Unit

  def query(query: EventQuery): Future[query.Response]
}

object SystemEventMonitor {

  /**
    * A no-op event monitor
    */
  object DevNull extends SystemEventMonitor {
    override def accept(event: RecordedEvent): Unit = {}

    override def query(query: EventQuery): Future[query.Response] = Future.failed(new Exception(s"dev/null ignoring $query"))
  }

  /**
    * A monitor which writes event asynchronously under the given directory
    *
    * @param dir    the directory under which events will be written
    * @param system the actor factory used to create an underlying actor
    * @return a SystemEventMonitor
    */
  def apply(dir: Path)(implicit system: ActorRefFactory) = {
    val dao = EventDao(dir)
    val actor = system.actorOf(Props(new ActorMonitor(dao)), "systemEventMonitor")
    new ActorMonitorClient(actor, s"SystemMonitor(${dir})")
  }

  /**
    * an alternate to the ask pattern to wrap a query and the result future
    */
  private case class EventQueryMessage[T](query: EventQuery.Aux[T], response: Promise[T])

  /**
    * A facade of an [[SystemEventMonitor]] which sends messages to an [[ActorMonitor]]
    *
    * @param actorMonitor the underlying actor ref to receive the messages
    */
  private class ActorMonitorClient(actorMonitor: ActorRef, override val toString: String) extends SystemEventMonitor {
    override def accept(event: RecordedEvent): Unit = {
      actorMonitor ! event
    }

    override def query(query: EventQuery): Future[query.Response] = {
      val promise = Promise[query.Response]()
      val aux : EventQuery.Aux[query.Response] = query.asInstanceOf[EventQuery.Aux[query.Response]]
      actorMonitor ! EventQueryMessage(aux, promise)
      promise.future
    }
  }

  /**
    * Simple actor to receive messages from an [[ActorMonitorClient]]
    *
    * @param monitor
    */
  private class ActorMonitor(monitor: SystemEventMonitor) extends BaseActor {
    override def receive: Receive = {
      case event: RecordedEvent => monitor.accept(event)
      case EventQueryMessage(query, promise) =>
        val future: Future[query.Response] = monitor.query(query)
        promise.tryCompleteWith(future)
    }
  }

}


