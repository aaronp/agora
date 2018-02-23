package agora.exec.test.scenario

import agora.exec.test.ExecutionIntegrationTest

object AnotherConf extends App {

  val fut = ExecutionIntegrationTest.anotherConfig.start()
  println("Running...")
}
