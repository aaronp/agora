package agora.exec.session

import agora.api.exchange.{JobPredicate, WorkSubscription}
import agora.exec.model.RunProcess
import agora.rest.BaseSpec

class SessionMessageTest extends BaseSpec {
  "OpenSession.asJob and prepareSubscription" should {
    "match one another" in {
      val subscription = OpenSession.prepareSubscription(WorkSubscription())

      JobPredicate().matches(OpenSession.asJob("some session id"), subscription) shouldBe true
      JobPredicate().matches(OpenSession.asJob("another session id"), subscription) shouldBe true
      JobPredicate().matches(CloseSession.asJob("meh"), subscription) shouldBe false
    }
  }
  "CloseSession.asJob and prepareSubscription" should {
    "match one another" in {
      val subscription = CloseSession.prepareSubscription("some session id", WorkSubscription())

      JobPredicate().matches(CloseSession.asJob("some session id"), subscription) shouldBe true
      JobPredicate().matches(CloseSession.asJob("another session id"), subscription) shouldBe false
      JobPredicate().matches(OpenSession.asJob("meh"), subscription) shouldBe false
    }
  }
  "UseSession.asJob and prepareSubscription" should {
    "match one another" in {
      val upload1 = UseSession.prepareUploadSubscription(WorkSubscription(), "first session")
      val exec1   = UseSession.prepareExecSubscription(WorkSubscription(), "first session")
      val upload2 = UseSession.prepareUploadSubscription(WorkSubscription(), "another session")

      JobPredicate().matches(UseSession.asUploadJob("first session"), upload1) shouldBe true
      JobPredicate().matches(UseSession.asUploadJob("another session"), upload1) shouldBe false

      JobPredicate().matches(UseSession.asUploadJob("another session"), upload2) shouldBe true

      JobPredicate().matches(UseSession.asExecJob("first session", RunProcess("execute")), exec1) shouldBe true
      JobPredicate().matches(UseSession.asExecJob("first session", RunProcess("execute")), upload1) shouldBe false
      JobPredicate().matches(UseSession.asExecJob("unknown", RunProcess("execute")), upload1) shouldBe false

      JobPredicate().matches(OpenSession.asJob("first session"), upload1) shouldBe false

    }
  }
}
