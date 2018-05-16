package lupin.pub.flatmap

import lupin.{BaseFlowSpec, Publishers}
import org.reactivestreams.Publisher

import scala.concurrent.Future

class FlatMapPublisherTest extends BaseFlowSpec {

  "PublisherImplicits.flatMap" should {
    "flatMap publishers" in {
      import lupin.implicits._
      val pubs: Publisher[Int] = Publishers.of(1, 2, 3, 4)

      val flatMapped: Publisher[Int] = pubs.flatMap { i =>
        val newList: List[Int] = List(100, 200, 300).map(_ + i)
        Publishers.forValues(newList)
      }

      val fut: Future[List[Int]] = flatMapped.collect()
      val expected: List[Int] = List(101, 201, 301, 102, 202, 302, 103, 203, 303, 104, 204, 304)
      fut.futureValue shouldBe expected
    }
  }
}
