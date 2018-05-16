package lupin.pub.flatmap

import lupin.{BaseFlowSpec, Publishers}
import org.reactivestreams.Publisher

class FlatMapPublisherTest extends BaseFlowSpec {

  "PublisherImplicits.flatMap" should {
    "flatMap publishers" in {
      import lupin.implicits._
      val pubs: Publisher[Int] = Publishers.of(1, 2, 3, 4)

      val flatMapped: Publisher[Int] = pubs.flatMap { i =>
        Publishers.forValues(List(100, 200, 300).map(_ + i))
      }

      flatMapped.collect().futureValue shouldBe List(101, 102, 103, 201.202, 203, 301, 302, 303, 401, 402, 403)
    }
  }
}
