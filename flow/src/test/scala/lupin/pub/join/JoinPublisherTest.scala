package lupin.pub.join

import lupin.{BaseFlowSpec, ListSubscriber, Publishers}
import org.reactivestreams.Publisher
import org.scalatest.GivenWhenThen

class JoinPublisherTest extends BaseFlowSpec with GivenWhenThen {

  "JoinPublisher" should {
    "send values from both publishers" in {
      val joined = JoinPublisher(Publishers.of(1, 2, 3), Publishers.of(4, 5, 6, 7, 8))

      val sub1 = new ListSubscriber[TupleUpdate[Int, Int]]
      joined.subscribe(sub1)

      val sub2 = new ListSubscriber[TupleUpdate[Int, Int]]
      joined.subscribe(sub2)

      sub1.request(4)
      eventually {
        val actual: List[TupleUpdate[Int, Int]] = sub1.receivedInOrderReceived()

        println(actual)



        actual shouldBe List(
          BothUpdated(1, 4),
          BothUpdated(2, 5)
        )
      }


      sub2.request(2)
      eventually {
        sub2.receivedInOrderReceived() shouldBe List(
          BothUpdated(1, 4)
        )
      }

      sub2.request(10)
      eventually {
        sub2.receivedInOrderReceived() shouldBe List(
          BothUpdated(1, 4),
          BothUpdated(2, 5),
          BothUpdated(3, 6),
          BothUpdated(4, 7),
          RightUpdate(8)
        )
      }
    }
  }
}
