package jabroni.exec

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import jabroni.rest.BaseSpec
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}

class LocalRunnerTest extends BaseSpec with ProcessRunnerTCK with BeforeAndAfter with BeforeAndAfterAll {

  implicit val sys = ActorSystem()
  implicit val mat = ActorMaterializer()

  override def afterAll() = {
    mat.shutdown()
    sys.terminate()
  }

  override def runner = ProcessRunner.local(Option("target/localRunnerTest".asPath.mkDirs()))

}
