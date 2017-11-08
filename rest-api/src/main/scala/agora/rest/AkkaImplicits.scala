package agora.rest

import akka.actor.ActorSystem
import akka.http.scaladsl.{Http, HttpExt}
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor}
import scala.util.{Failure, Success}

class AkkaImplicits(val actorSystemName: String, actorConfig: Config) extends AutoCloseable with StrictLogging {
  logger.debug(s"Creating actor system $actorSystemName")
  implicit val system = {
    val sys = ActorSystem(actorSystemName, actorConfig)
    import sys.dispatcher
    sys.whenTerminated.onComplete {
      case Success(_)   => logger.debug(s"$actorSystemName terminated")
      case Failure(err) => logger.warn(s"$actorSystemName terminated w/ $err")
    }
    sys
  }
  implicit val materializer                       = ActorMaterializer()
  implicit val executionContext: ExecutionContext = system.dispatcher
  val http: HttpExt                               = Http()

  override def close(): Unit = {
    materializer.shutdown()
    system.terminate()
  }
}
