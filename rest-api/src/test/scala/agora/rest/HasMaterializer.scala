package agora.rest

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll

import scala.concurrent.ExecutionContext
import scala.util.Try

trait HasMaterializer extends BeforeAndAfterAll { this: org.scalatest.BeforeAndAfterAll with org.scalatest.Suite =>

  private[this] def systemConf = ConfigFactory.load("test-system")

  private[this] var systemOpt: Option[ActorSystem]             = None
  private[this] var materializerOpt: Option[ActorMaterializer] = None

  implicit def execContext: ExecutionContext = materializer.executionContext

  implicit def system: ActorSystem = systemOpt.getOrElse {
    val newSystem = ActorSystem(getClass.getSimpleName.filter(_.isLetter), systemConf).ensuring(_.settings.Daemonicity)
    systemOpt = Option(newSystem)
    newSystem
  }

  implicit def materializer: ActorMaterializer = materializerOpt.getOrElse {
    val newMat = ActorMaterializer()
    materializerOpt = Option(newMat)
    newMat
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    materializerOpt.foreach(_.shutdown())
    systemOpt.foreach(_.terminate())
  }
}
