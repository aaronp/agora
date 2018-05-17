package lupin.pub.flatmap

import lupin.implicits._
import lupin.{BaseFlowSpec, ListSubscriber, Publishers}
import org.reactivestreams.Publisher

import scala.concurrent.Future

class FlatMapPublisherTest extends BaseFlowSpec {

  val expected: List[Int] = List(101, 201, 301, 102, 202, 302, 103, 203, 303, 104, 204, 304)

  "PublisherImplicits.flatMap" should {
    "collect all elements in order" in {
      val pubs: Publisher[Int] = Publishers.of(1, 2, 3, 4)

      val flatMapped: Publisher[Int] = pubs.flatMap { i =>
        val newList: List[Int] = List(100, 200, 300).map(_ + i)
        Publishers.forValues(newList)
      }

      val fut: Future[List[Int]] = flatMapped.collect()
      fut.futureValue shouldBe expected
    }
    "process a single element" in {
      val pubs: Publisher[Int] = Publishers.of(1, 2, 3, 4)

      val flatMapped: Publisher[Int] = pubs.flatMap { i =>
        val newList: List[Int] = List(100, 200, 300).map(_ + i)
        Publishers.forValues(newList)
      }

      val sub = flatMapped.subscribeWith(new ListSubscriber[Int]())
      sub.request(1)
      eventually {
        sub.receivedInOrderReceived() shouldBe expected.headOption.toList
      }
    }
    "process elements one at a time" in {
      val pubs: Publisher[Int] = Publishers.of(1, 2, 3, 4)

      val flatMapped: Publisher[Int] = pubs.flatMap { i =>
        val newList: List[Int] = List(100, 200, 300).map(_ + i)
        Publishers.forValues(newList)
      }

      val sub = flatMapped.subscribeWith(new ListSubscriber[Int]())
      expected.inits.toList.reverse.tail.foreach { list =>
        println(list)
        sub.request(1)
        eventually {
          sub.receivedInOrderReceived() shouldBe list
        }
      }
    }
  }
}
