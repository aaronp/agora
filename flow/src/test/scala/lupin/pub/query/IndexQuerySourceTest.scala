package lupin.pub.query

import lupin.data.Accessor
import lupin.{BaseFlowSpec, Publishers}
import org.reactivestreams.Publisher

class IndexQuerySourceTest extends BaseFlowSpec {
  "IndexQuerySource" should {
    "return the latest values as well as any updates for an index query" in {

      import lupin.implicits._

      case class Foo(id: Int, name: String, someProperty: String, count: Int)
      implicit object FooId extends Accessor[(Long, Foo), Int] {
        override def get(value: (Long, Foo)): Int = value._2.id
      }
      def next(lastValue: Option[Foo]) = {
        val foo = lastValue.fold(Option(Foo(0, "first foo", "alpha!", 0))) {
          case Foo(_, _, _, 20) => None
          case previous =>
            val x      = previous.id
            val newID  = (x + 1) % 5
            val newIdx = previous.count + 1
            Option(Foo(newID, s"$newIdx w/ ${x % 7} for id $newID", "property" + x, newIdx))
        }
        foo -> true
      }

      val sequenced = Publishers.sequenced[Foo]()
      Publishers.unfold(next).subscribe(sequenced)

      // create the query source under test
      val indexedNames: Publisher[IndexedValue[Int, String]] = IndexQuerySource(sequenced) { foo =>
        foo.name
      }

      val allReceived = indexedNames.foreach { value =>
        println(value)

      }

      allReceived.futureValue shouldBe true

      println("done")
    }
  }

}
