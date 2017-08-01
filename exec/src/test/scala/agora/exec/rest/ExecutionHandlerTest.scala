package agora.exec.rest

import agora.api.exchange.JobPredicate
import agora.exec.model.RunProcess
import agora.rest.BaseSpec
import io.circe.generic.auto._

class ExecutionHandlerTest extends BaseSpec {

  "ExecutionHandler.newWorkspaceSubscription" should {
    "match RunProcess jobs" in {
      import agora.api.Implicits._

      val job = RunProcess("pwd").asJob

      val subscription = ExecutionHandler.newWorkspaceSubscription("workspace", Set("file.one"))

      JobPredicate().matches(job, subscription) shouldBe true
    }
  }
}
