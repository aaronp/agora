package jabroni.rest

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.StrictLogging

import scala.util.{Failure, Success}

class AkkaImplicits(val actorSystemName: String) extends StrictLogging {
  logger.debug(s"Creating actor system $actorSystemName")
  implicit val system = {
    val sys = ActorSystem(actorSystemName)
    import sys.dispatcher
    sys.whenTerminated.onComplete {
      case Success(_) => logger.warn(s"$actorSystemName terminated")
      case Failure(err) => logger.warn(s"$actorSystemName terminated w/ $err")
    }
    sys
  }
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher
  val http = Http()
}
