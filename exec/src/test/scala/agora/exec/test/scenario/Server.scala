package agora.exec.test.scenario

import agora.exec.ExecConfig
import agora.exec.test.ExecutionIntegrationTest

object Server extends App {

  val fut = ExecutionIntegrationTest.firstConfig.start()
  println("Running...")
}
