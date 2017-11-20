package agora.rest.logging

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.AppenderBase

import scala.collection.mutable.ListBuffer

class BufferedAppender private (initialName: String) extends AppenderBase[ILoggingEvent] {
  private var myName       = initialName
  private val eventsBuffer = ListBuffer[ILoggingEvent]()

  override def getName: String = myName

  override def setName(name: String) = {
    myName = name
  }

  override def append(eventObject: ILoggingEvent) = {

    eventsBuffer += eventObject
  }

  def events = eventsBuffer.toList

  def logs: List[String] = events.map { event =>
    event.getFormattedMessage
  }

}

object BufferedAppender {
  private val counter = new AtomicInteger(0)
  def nextName()      = s"BufferedAppender${counter.incrementAndGet()}"

  def apply() = {
    val appender = new BufferedAppender(BufferedAppender.nextName())
    appender.start()
    appender
  }
}
