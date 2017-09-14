package agora.io

import akka.actor.Actor
import com.typesafe.scalalogging.StrictLogging

abstract class BaseActor extends Actor with StrictLogging {

  override def unhandled(message: Any): Unit = {
    super.unhandled(message)
    sys.error(s"${getClass.getSimpleName}(s${self.path}) couldn't handle $message")
  }
}
