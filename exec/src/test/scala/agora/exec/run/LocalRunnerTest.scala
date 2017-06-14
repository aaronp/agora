package agora.exec.run

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import agora.exec.dao.UploadDao
import agora.rest.BaseSpec
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}

class LocalRunnerTest extends BaseSpec with ProcessRunnerTCK with BeforeAndAfter with BeforeAndAfterAll {

  implicit val sys = ActorSystem()
  implicit val mat = ActorMaterializer()

  override def afterAll() = {
    mat.shutdown()
    sys.terminate()
  }

  override def runner = {
    val dir = "target/localRunnerTest".asPath.mkDirs()
    ProcessRunner.local(UploadDao(dir), Option(dir))
  }

}
