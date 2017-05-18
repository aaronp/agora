package jabroni.exec

import java.nio.file.Path

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import jabroni.rest.BaseSpec
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}

class LocalRunnerTest extends BaseSpec with ProcessRunnerTCK with BeforeAndAfter with BeforeAndAfterAll {

  implicit val sys = ActorSystem()
  implicit val mat = ActorMaterializer()
  val dir: Path = "target/localRunnerTest".asPath

  override def afterAll() = {
    mat.shutdown()
    sys.terminate()
  }

  before {
    dir.mkDirs()
  }

  after {
    dir.delete()
  }

  override def runner = ProcessRunner(dir, true)

}
