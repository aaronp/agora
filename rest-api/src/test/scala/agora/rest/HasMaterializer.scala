package agora.rest

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.scalatest.BeforeAndAfterAll

trait HasMaterializer extends BeforeAndAfterAll { this: org.scalatest.BeforeAndAfterAll with org.scalatest.Suite =>

  private[this] var materialiazerCreated = false
  implicit val system                    = ActorSystem(getClass.getSimpleName.filter(_.isLetter))

  implicit lazy val materializer: ActorMaterializer = {
    materialiazerCreated = true
    ActorMaterializer()
  }

  override protected def afterAll(): Unit = {
    super.afterAll()
    if (materialiazerCreated) {
      materializer.system.terminate
      materializer.shutdown()
    }
  }
}
