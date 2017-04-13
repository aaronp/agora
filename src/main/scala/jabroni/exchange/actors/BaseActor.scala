package jabroni.exchange.actors

import akka.actor.Actor

abstract class BaseActor extends Actor {

  override def unhandled(message: Any): Unit = {
    super.unhandled(message)
    sys.error(s"${self.path} couldn't handle $message")
  }

}
