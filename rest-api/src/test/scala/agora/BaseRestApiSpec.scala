package agora

import agora.rest.AkkaImplicits

import scala.concurrent.Future

abstract class BaseRestApiSpec extends BaseApiSpec {

  var beforeThreads: Set[Thread] = Set.empty

  def scalatestThreads = Set(
    "Reference Handler",
    "ScalaTest-dispatcher",
    "ScalaTest-run",
    "Signal Dispatcher"
  )

  override def beforeAll(): Unit = {
    beforeThreads = AkkaImplicits.allThreads()
    super.beforeAll()
  }
}
