package miniraft.state

import agora.rest.BaseSpec
import miniraft.{AppendEntriesResponse, UpdateResponse}

import scala.concurrent.Promise

class UpdateResponseTest extends BaseSpec {

  import scala.concurrent.ExecutionContext.Implicits._

  val ok   = AppendEntriesResponse(Term(123), true, 456)
  val nope = AppendEntriesResponse(Term(123), false, 456)

  "UpdateResponse" should {
    "complete the future when most acks come back successfully" in {
      val responses = (1 to 5).map(i => i.toString -> Promise[AppendEntriesResponse]()).toMap
      val resp      = UpdateResponse.Appendable(5, responses)

      resp.result.isCompleted shouldBe false
      responses("1").trySuccess(ok)
      resp.result.isCompleted shouldBe false
      responses("2").trySuccess(ok)
      resp.result.isCompleted shouldBe false
      responses("5").trySuccess(ok)
      resp.result.futureValue shouldBe true

    }
    "complete the future with false when most acks come back unsuccessfully" in {
      val responses = (1 to 5).map(i => i.toString -> Promise[AppendEntriesResponse]()).toMap
      val resp      = UpdateResponse.Appendable(5, responses)

      resp.result.isCompleted shouldBe false
      responses("1").trySuccess(nope)
      resp.result.isCompleted shouldBe false
      responses("2").trySuccess(ok)
      resp.result.isCompleted shouldBe false
      responses("5").trySuccess(nope)
      resp.result.isCompleted shouldBe false
      responses("4").trySuccess(nope)
      resp.result.futureValue shouldBe false

    }
  }

}
