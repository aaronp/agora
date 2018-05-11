package lupin.pub.query

import lupin.data.Accessor
import lupin.example.IndexSelection
import lupin.pub.query.Indexer.QueryIndexer
import lupin.{BaseFlowSpec, ListSubscriber, Publishers}
import org.reactivestreams.Publisher
import org.scalatest.GivenWhenThen

import scala.collection.mutable.ListBuffer

class IndexerTest extends BaseFlowSpec with GivenWhenThen {

  // create some example data type
  case class Person(id: Int, name: String)

  // .. and put in scope an accessor for obtaining an ID for our new type
  implicit object PersonIdAccessor extends Accessor[Person, Int] {
    override def get(value: Person) = value.id
  }

  "Indexer.index" should {
    "return values w/ their indices" in {

      val indexer: QueryIndexer[Int, String] = Indexer.slowInMemoryIndexer[Int, String]

      val fields = List("alpha", "beta", "gamma", "delta", "epsilon")
      val indexedValues = ListBuffer[IndexedValue[Int, String]]()
      val querySource = fields.zipWithIndex.foldLeft(indexer) {
        case (dao, (str, index)) =>
          val id = index + 100 // make up some different ID
        val op = CrudOperation.create(id)
          val (newIndexer, value) = dao.index(index, str, op)
          indexedValues += value
          newIndexer.asInstanceOf[QueryIndexer[Int, String]]
      }

      indexedValues.toList shouldBe List(
        IndexedValue(0, 100, IndexOperation.newIndex(0, "alpha")),
        IndexedValue(1, 101, IndexOperation.newIndex(1, "beta")),
        IndexedValue(3, 103, IndexOperation.newIndex(2, "delta")),
        IndexedValue(4, 104, IndexOperation.newIndex(2, "epsilon")),
        IndexedValue(2, 102, IndexOperation.newIndex(2, "gamma"))
      )

      import lupin.implicits._
      val results = querySource.query(IndexSelection(0, fields.size))

      val updates: List[IndexQueryResult[Int, String]] = results.collect().futureValue
      updates shouldBe List(
        IndexQueryResult(0, 100, 0, "alpha"),
        IndexQueryResult(1, 101, 1, "beta"),
        IndexQueryResult(3, 103, 2, "delta"),
        IndexQueryResult(4, 104, 3, "epsilon"),
        IndexQueryResult(2, 102, 4, "gamma")
      )


      val newI = querySource.asInstanceOf[QueryIndexer[Int, String]]
      // change 'delta' to 'zeta'
      val q =  newI.index(500, "zeta", CrudOperation.update(103))
      println(q)

    }
  }

  "Indexer.crud" should {
    "publish initial elements meeting the criteria" in {


      val people = Publishers.of(
        Person(1, "Georgina"),
        Person(2, "Eleanor"),
        Person(3, "Jayne"),
        Person(1, "George")
      )

      val dao: Publisher[(CrudOperation[Int], Person)] = Indexer.crud(people)

      val initialLoadListener = new ListSubscriber[(CrudOperation[Int], Person)]
      dao.subscribe(initialLoadListener)

      initialLoadListener.request(10) // load up our DAO prior to any new subscriptions
      eventually {
        initialLoadListener.receivedInOrderReceived().size shouldBe 4
      }

      val crudListener = new ListSubscriber[(CrudOperation[Int], Person)]
      import lupin.implicits._
      dao.filter(p => Set(1, 3).contains(p._1.key)).subscribe(crudListener)

      crudListener.request(5)
      eventually {
        crudListener.receivedInOrderReceived() should contain only(
          Create(1) -> Person(1, "Georgina"),
          Create(3) -> Person(3, "Jayne"),
          Update(1) -> Person(1, "George")
        )
      }
    }

    "publish CRUD updates for filtered elements" in {

      Given("Some data publisher")
      val people = Publishers.of(
        Person(1, "Georgina"),
        Person(2, "Eleanor"),
        Person(3, "Jayne"),
        Person(1, "George")
      )

      When("we subscribe to the data w/ a CRUD publisher")
      val dao: Publisher[(CrudOperation[Int], Person)] = Indexer.crud(people)

      And("observe the data through the DaoProcessor")
      val crudListener = new ListSubscriber[(CrudOperation[Int], Person)]
      dao.subscribe(crudListener)

      crudListener.receivedInOrderReceived() shouldBe Nil

      Then("We should see Create values for the data requested")
      crudListener.request(1)
      eventually {
        crudListener.receivedInOrderReceived() shouldBe List(Create(1) -> (Person(1, "Georgina")))
      }

      // request the next element
      crudListener.request(1)
      eventually {
        crudListener.receivedInOrderReceived() shouldBe List(Create(1) -> (Person(1, "Georgina")), Create(2) -> (Person(2, "Eleanor")))
      }

      When("The last element is requested, it should see an update")
      // request the next element, an update to Georgina
      crudListener.request(2)

      eventually {
        crudListener.receivedInOrderReceived() should contain inOrder(
          Create(1) -> (Person(1, "Georgina")),
          Create(2) -> (Person(2, "Eleanor")),
          Create(3) -> (Person(3, "Jayne")),
          Update(1) -> (Person(1, "George"))
        )
      }

      And("complete as the upstream data source is complete")
      eventually {
        crudListener.isCompleted() shouldBe true
      }
    }
  }
}
