package agora.rest

import java.io.Closeable

import akka.actor.{ActorSystem, Cancellable, LightArrayRevolverScheduler, Terminated}
import akka.http.scaladsl.{Http, HttpExt}
import akka.stream.ActorMaterializer
import com.typesafe.config.Config
import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

class AkkaImplicits(val actorSystemName: String, actorConfig: Config) extends AutoCloseable with StrictLogging {
  logger.debug(s"Creating actor system $actorSystemName")
  implicit val system = {
    val sys = ActorSystem(actorSystemName, actorConfig)

    sys.whenTerminated.onComplete {
      case Success(_) => logger.info(s"$actorSystemName terminated")
      case Failure(err) => logger.warn(s"$actorSystemName terminated w/ $err")
    }(ExecutionContext.global)
    sys
  }
  implicit val materializer = ActorMaterializer()
  implicit val executionContext: ExecutionContext = system.dispatcher
  implicit val http: HttpExt = Http()

  override def close(): Unit = stop()

  def stop(): Future[Terminated] = {
    materializer.shutdown()
    system.terminate()
  }

  private lazy val SystemThreadName = s"${actorSystemName}-.*-\\d+".r

  def stopThreads() = {
    threads().foreach { t =>

      def doInterrupt() = {
        try {
          t.interrupt()
        } catch {
          case e: Throwable =>
            logger.error(s"Interrupting ${t.getName} threw $e")
        }
      }
      try {
        val target = AkkaImplicits.ThreadTarget.get(t)
        target match {
          case null =>
            logger.error(s"No runnable set on ${t.getName}")
            t.stop()
          case c: AutoCloseable =>
            logger.info(s"Force-closing runnable on ${t.getName}")
            c.close()
            doInterrupt()
          case c: Closeable =>
            logger.info(s"Force-closing runnable on ${t.getName}")
            c.close()
            doInterrupt()
          case c: Cancellable =>
            logger.info(s"Force-cancelling cancellable (isCancelled=${c.isCancelled}) on ${t.getName}")
            c.cancel()
            doInterrupt()
//          case lars : LightArrayRevolverScheduler  =>
          case other  =>
            logger.error(s"Runnable '$other' on ${t.getName} is not closeable")
            doInterrupt()
        }

      } catch {
        case NonFatal(e) =>
          logger.error(s"Error getting thread target for ${t.getName}: $e")
          doInterrupt()
        case td : ThreadDeath =>

      }

    }
  }

  def threads(): Set[Thread] = {

    AkkaImplicits.allThreads().filter { t =>
      t.getName match {
        case SystemThreadName() => true
        case _ => false
      }
    }
  }
}

object AkkaImplicits {
  val ThreadTarget = {
    val tgt = classOf[Thread].getDeclaredField("target")
    tgt.setAccessible(true)
    tgt
  }

  def allThreads() = {
    import scala.collection.JavaConverters._
    Thread.getAllStackTraces.keySet().asScala.toSet
  }
}
