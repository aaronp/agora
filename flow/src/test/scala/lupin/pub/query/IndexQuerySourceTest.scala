package lupin.pub.query

import java.util.concurrent.atomic.AtomicLong

import lupin.data.Accessor
import lupin.pub.FIFO
import lupin.{BaseFlowSpec, ListSubscriber, Publishers}
import org.reactivestreams.Publisher
import org.scalatest.GivenWhenThen

class IndexQuerySourceTest extends BaseFlowSpec with GivenWhenThen {
  "IndexQuerySource.forSequencedDataFeed" should {
    "return results for a specific index" in {

      Given("An IndexQuerySource created from a CRUD data feed")
      case class Person(id: String, name: String, age: Int)
      type PersonUpdate = (CrudOperation[String], (Long, Person))
      val updates: FIFO[Option[PersonUpdate]] = FIFO[Option[PersonUpdate]]()
      val data = Publishers(updates)

      import lupin.implicits._


      val nameQuery: IndexQuerySource[String, String] = IndexQuerySource.fromSequencedDataFeed(data)(_.name)
      val ageQuery = IndexQuerySource.fromSequencedDataFeed(data)(_.age)

      And("A query for an index selection of indices 1 and 2")
      val firstTwoNames: Publisher[IndexedEntry[String, String]] = nameQuery.between(1, 2)

      val updateListener = firstTwoNames.withSubscriber(new ListSubscriber[IndexedEntry[String, String]]())

      When("The first value is inserted")
      val seqNo = new AtomicLong(0)
      updates.enqueue(Option(CrudOperation.create("uuid-1") -> (seqNo.incrementAndGet(), Person("uuid-1", "Henry", 3))))

      Then("no values should be available")
      Thread.sleep(testNegativeTimeout.toMillis)
      updateListener.received().size shouldBe 0
    }
  }


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
            val x = previous.id
            val newID = (x + 1) % 5
            val newIdx = previous.count + 1
            Option(Foo(newID, s"$newIdx w/ ${x % 7} for id $newID", "property" + x, newIdx))
        }
        foo -> true
      }

      val sequenced = Publishers.sequenced[Foo]()
      Publishers.unfold(next).subscribe(sequenced)

      // create the query source under test
      val indexedNamesSrc: IndexQuerySource[Int, String] = IndexQuerySource(sequenced) { foo =>
        foo.name
      }

      val indexedNames = indexedNamesSrc.between(0, 1000)

      val allReceived = indexedNames.foreach { value =>
        println(value)

      }

      allReceived.futureValue shouldBe true

      println("done")
    }
  }

}
