package agora.rest

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.ExecutionContext

trait HasMaterializer extends BeforeAndAfterAll with ScalaFutures { this: org.scalatest.BeforeAndAfterAll with org.scalatest.Suite =>

  implicit def execContext: ExecutionContext = materializer.executionContext

  private var systemCreated       = false
  private var materializerCreated = false

  implicit lazy val system: ActorSystem = {
    systemCreated = true
    ActorSystem(getClass.getSimpleName.filter(_.isLetter), HasMaterializer.systemConf).ensuring(_.settings.Daemonicity)
  }

  implicit lazy val materializer: ActorMaterializer = {
    materializerCreated = true
    ActorMaterializer()(system)
  }

  override protected def afterAll(): Unit = {
    if (materializerCreated) {
      materializer.shutdown()
    }
    if (systemCreated) {
      system.terminate().futureValue
    }
    super.afterAll()
  }

}

object HasMaterializer {

  lazy val systemConf = ConfigFactory.load("test-system")
}
