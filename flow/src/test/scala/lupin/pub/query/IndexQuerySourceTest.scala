package lupin.pub.query

import lupin.data.Accessor
import lupin.{BaseFlowSpec, Publishers}
import org.reactivestreams.Publisher

class IndexQuerySourceTest extends BaseFlowSpec {
  "IndexQuerySource" should {
    "return the latest values as well as any updates for an index query" in {

      import lupin.implicits._

      case class Foo(id: Int, name: String, someProperty: String)
      implicit object FooId extends Accessor[(Long, Foo), Int] {
        override def get(value: (Long, Foo)): Int = value._2.id
      }
      def next(lastValue: Option[Foo]) = {
        val foo = lastValue.fold(Option(Foo(0, "first foo", "alpha!"))) {
          case Foo(100, _, _) =>

            None
          case previous =>
            val x = previous.id
            Option(Foo(x + 1 % 5, s"$x mod 7 is ${x % 7}", "property" + x))
        }
        println(s"generated from $lastValue returning $foo")
        foo -> true
      }

      val sequenced = Publishers.sequenced[Foo]()
      Publishers.unfold(next).subscribe(sequenced)

      // create the query source under test
      val indexedNames: Publisher[IndexedValue[Int, String]] = IndexQuerySource(sequenced) { foo =>
        foo.name
      }

      indexedNames.foreach { value =>

        println(value)

      }


      println("done")
    }
  }

}
