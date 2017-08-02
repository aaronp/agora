package agora.exec.run

import agora.rest.BaseSpec
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}

import scala.concurrent.ExecutionContext.Implicits.global

class LocalRunnerTest extends BaseSpec with ProcessRunnerTCK with BeforeAndAfter with BeforeAndAfterAll {

  implicit val sys = ActorSystem()
  implicit val mat = ActorMaterializer()

  override def afterAll() = {
    mat.shutdown()
    sys.terminate()
  }

  override def runner = {
    val dir = "target/localRunnerTest".asPath.mkDirs()
    ProcessRunner(Option(dir))
  }

}
