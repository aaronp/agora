package agora.rest

import agora.api.data.Lazy
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.ConfigFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.concurrent.ScalaFutures

import scala.concurrent.ExecutionContext

trait HasMaterializer extends BeforeAndAfterAll with ScalaFutures {
  this: org.scalatest.BeforeAndAfterAll with org.scalatest.Suite =>

  implicit def execContext: ExecutionContext = materializer.executionContext

  val lazySystem = Lazy {
    ActorSystem(getClass.getSimpleName.filter(_.isLetter), HasMaterializer.systemConf).ensuring(_.settings.Daemonicity)
  }

  implicit def system = lazySystem.value

  val lazyMaterializer = Lazy {
    ActorMaterializer()(system)
  }

  implicit def materializer: ActorMaterializer = lazyMaterializer.value

  override def afterAll(): Unit = {
    lazyMaterializer.foreach(_.shutdown())
    lazySystem.foreach(_.terminate().futureValue)
    super.afterAll()
  }

}

object HasMaterializer {

  lazy val systemConf = ConfigFactory.load("test-system").ensuring(!_.isEmpty, "couldn't load 'test-system'")
}
