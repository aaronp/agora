package lupin.pub.query

import java.util.concurrent.atomic.AtomicLong

import lupin.data.Accessor
import lupin.pub.FIFO
import lupin.pub.sequenced.SequencedProcessorInstance
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
      val data: Publisher[(CrudOperation[String], (Long, Person))] = Publishers(updates)

      import lupin.implicits._

      val sequencedData: Publisher[Sequenced[(CrudOperation[String], String)]] = data.map {
        case (op: CrudOperation[String], (seqNo, person)) => Sequenced[(CrudOperation[String], String)](seqNo, op -> person.name)
      }
      val nameIndexer: Publisher[IndexedValue[String, String]] with IndexQuerySource[String, String] = IndexQuerySource.fromSequencedUpdates(sequencedData)

      And("A query for an index selection of indices 1 and 2")
      val firstTwoNames: Publisher[IndexedEntry[String, String]] = nameIndexer.between(1, 2)

      val indexListener = nameIndexer.withSubscriber(new ListSubscriber[IndexedValue[String, String]]())
      indexListener.request(10)
      val updateListener = firstTwoNames.withSubscriber(new ListSubscriber[IndexedEntry[String, String]]())
      updateListener.request(10)

      When("The first value is inserted")
      val seqNo = new AtomicLong(0)
      updates.enqueue(Option(CrudOperation.create("uuid-1") -> (seqNo.incrementAndGet(), Person("uuid-1", "Henry", 3))))
      // pull some data through the indexer


      Then("A value should be indexed, but the queried indices (1 to 2) shouldn't be available yet as it is outside the queried index range")
      eventually {
        indexListener.received().size shouldBe 1
      }
      updateListener.received() shouldBe empty
      indexListener.received() shouldBe List(IndexedValue(1, "uuid-1", NewIndex(0, "Henry")))

      When("The second value is inserted")
      updates.enqueue(Option(CrudOperation.create("uuid-2") -> (seqNo.incrementAndGet(), Person("uuid-2", "Eleanor", 2))))

      Then("We should see an update for the index")
      eventually {
        indexListener.received() shouldBe List(
          IndexedValue(2, "uuid-2", NewIndex(0, "Eleanor")),
          IndexedValue(1, "uuid-1", NewIndex(0, "Henry"))
        )
      }
      eventually {
        updateListener.received.size shouldBe 1
      }
      updateListener.received().head shouldBe IndexedEntry(1, 1, "uuid-2", "Eleanor")
    }
  }


  "IndexQuerySource" should {
    "return the latest values as well as any updates for an index query" in {

      import lupin.implicits._

      case class Foo(id: Int, name: String, someProperty: String, count: Int)
      implicit object FooId extends Accessor[Foo, Int] {
        override def get(value: Foo): Int = value.id
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

      val sequenced: SequencedProcessorInstance[Foo] = Publishers.sequenced[Foo]()
      Publishers.unfold(next).subscribe(sequenced)

      // create the query source under test
      val indexedNamesSrc = IndexQuerySource(sequenced.sequencePublisher()) { foo =>
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
