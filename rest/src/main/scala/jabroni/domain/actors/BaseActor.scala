package jabroni.domain.actors

import akka.actor.Actor

class BaseActor extends Actor {

  def eventStream = context.system.eventStream
  override def unhandled(message: Any): Unit = {
    super.unhandled(message)
    sys.error(s"${self.path} couldn't handle $message")
  }

  override def receive: Receive = ???
}
