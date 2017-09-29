package agora.rest

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.ExecutionContext
import scala.util.Try

trait HasMaterializer extends BeforeAndAfterAll with ScalaFutures {
  this: org.scalatest.BeforeAndAfterAll with org.scalatest.Suite =>

  implicit def execContext: ExecutionContext = materializer.executionContext

  implicit def system: ActorSystem = testSystem

  implicit def materializer: ActorMaterializer = matInstance

  private[this] lazy val matInstance = ActorMaterializer()

  private[this] def systemConf = ConfigFactory.load("test-system")

  private[this] lazy val testSystem: ActorSystem = {
    ActorSystem(getClass.getSimpleName.filter(_.isLetter), systemConf).ensuring(_.settings.Daemonicity)
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    matInstance.shutdown()
    Try(testSystem.terminate().futureValue)
  }

  override protected def beforeAll(): Unit = {
    super.afterAll()
  }
}

object HasMaterializer {}
