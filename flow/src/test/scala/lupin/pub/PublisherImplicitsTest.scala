package lupin.pub

import lupin.{BaseFlowSpec, Publishers}
import org.reactivestreams.Publisher
import org.scalatest.FunSuite

class PublisherImplicitsTest extends BaseFlowSpec {

  "PublisherImplicits.flatMap" should {
    "flatMap publishers" in {
      import lupin.implicits._
      val pubs: Publisher[Int] = Publishers.of(1,2,3,4,5)

      val x: PublisherImplicits.RichPublisher[Int, Publisher] = asRichPublisher(pubs)

      val y: PublisherImplicits.RichPublisher[Int, Publisher] = pubs

      x.flatMap { i =>
        Publishers.forValues(List(1,2,3,4,5).map(_ * i))
      }
    }
  }
}
