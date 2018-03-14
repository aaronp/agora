package agora.rest

import java.util.concurrent.atomic.AtomicInteger

import akka.actor.{ActorSystem, Terminated}
import akka.http.scaladsl.{Http, HttpExt}
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

class AkkaImplicits private (actorSystemPrefix: String, actorConfig: Config) extends AutoCloseable with StrictLogging {
  val actorSystemName = AkkaImplicits.uniqueName(actorSystemPrefix)

  logger.debug(s"Creating actor system $actorSystemName")
  @volatile private var terminated = false

  private def setTerminated() = {
    terminated = true
    AkkaImplicits.remove(this)
  }

  implicit val system = {
    val sys = ActorSystem(actorSystemName, actorConfig)

    sys.whenTerminated.onComplete {
      case Success(_) =>
        logger.info(s"$actorSystemName terminated")
        setTerminated()
      case Failure(err) =>
        logger.warn(s"$actorSystemName terminated w/ $err")
        setTerminated()
    }(ExecutionContext.global)
    sys
  }
  implicit val materializer                       = ActorMaterializer()
  implicit val executionContext: ExecutionContext = system.dispatcher
  implicit val http: HttpExt                      = Http()

  override def close(): Unit = stop()

  def isClosed(): Boolean = terminated

  def stop(): Future[Terminated] = {
    logger.warn(s"Shutting down actor system $actorSystemName")
    try {
      materializer.shutdown()
    } catch {
      case NonFatal(e) => logger.error(s"Error shutting down the materializer for $actorSystemName: ", e)
    }
    system.terminate()
  }

  def threads(): Set[Thread] = {
    val SystemThreadName = s"${actorSystemName}-.*-\\d+".r
    AkkaImplicits.allThreads().filter { t =>
      t.getName match {
        case SystemThreadName() => true
        case _                  => false
      }
    }
  }
}

object AkkaImplicits {

  private val uniqueInstanceCounter                 = new AtomicInteger(0)
  private def uniqueName(actorSystemPrefix: String) = s"$actorSystemPrefix${uniqueInstanceCounter.incrementAndGet()}"

  private def remove(implicits: AkkaImplicits) = {
    synchronized {
      instances = instances diff List(implicits)
    }
    require(!instances.contains(implicits))
  }

  private var instances: List[AkkaImplicits] = Nil

  def openInstances() = instances

  def apply(actorSystemName: String, actorConfig: Config): AkkaImplicits = {
    val inst = new AkkaImplicits(actorSystemName, actorConfig)
    synchronized {
      instances = inst :: instances
    }
    inst
  }

  def allThreads() = {
    import scala.collection.JavaConverters._
    Thread.getAllStackTraces.keySet().asScala.toSet
  }
}
