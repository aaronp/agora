package lupin.pub.query

import lupin.data.Accessor
import lupin.{BaseFlowSpec, Publishers}
import org.reactivestreams.Publisher

class IndexQuerySourceTest extends BaseFlowSpec {
  "IndexQuerySource" should {
    "return the latest values as well as any updates for an index query" in {


      case class Foo(id: Int, name: String, someProperty: String)
      implicit object FooId extends Accessor.Aux[Foo, Int] {

      }
      def next(lastValue: Option[Foo]): Option[Foo] = {
        println(s"generated from $lastValue")
        lastValue.fold(Option(Foo(0, "first foo", "alpha!"))) {
          case Foo(10, _, _) => None
          case previous =>
            val x = previous.id
            Option(Foo(x + 1, s"$x mod 3 is ${x % 3}", "property" + x))
        }
      }

      val sequenced = Publishers.sequenced[Foo]()
      Publishers.unfold(next).subscribe(sequenced)

      val indexedNames = IndexQuerySource(sequenced) { foo =>
        foo.name
      }

    }
  }

}
