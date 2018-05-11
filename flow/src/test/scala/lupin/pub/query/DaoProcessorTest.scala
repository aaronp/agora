package lupin.pub.query

import lupin.data.Accessor
import lupin.{BaseFlowSpec, ListSubscriber, Publishers}
import org.reactivestreams.Publisher
import org.scalatest.GivenWhenThen

class DaoProcessorTest extends BaseFlowSpec with GivenWhenThen {

  // create some example data type
  case class Person(id: Int, name: String)

  // .. and put in scope an accessor for obtaining an ID for our new type
  implicit object PersonIdAccessor extends Accessor[Person] {
    override type Index = Int

    override def get(value: Person): Index = value.id
  }

  "DaoProcessor" should {
    "publish initial elements meeting the criteria" in {


      val people = Publishers.of(
        Person(1, "Georgina"),
        Person(2, "Eleanor"),
        Person(3, "Jayne"),
        Person(1, "George")
      )

      val dao: Publisher[(CrudOperation[Int], Person)] = Indexer.crud(people)
//      people.subscribe(dao)

      val initialLoadListener = new ListSubscriber[(CrudOperation[Int], Person)]
      dao.subscribe(initialLoadListener)

      initialLoadListener.request(10) // load up our DAO prior to any new subscriptions
      eventually {
        initialLoadListener.receivedInOrderReceived().size shouldBe 4
      }

      val crudListener = new ListSubscriber[(CrudOperation[Int], Person)]
      import lupin.implicits._
      dao.filter(p => Set(1,3).contains(p._1.key)).subscribe(crudListener)

      crudListener.request(5)
      eventually {
        crudListener.receivedInOrderReceived() should contain only (
          Create(1) -> Person(1, "Georgina"),
          Create(3) -> Person(3, "Jayne"),
          Update(1) -> Person(1, "George")
        )
      }
    }

//    "publish CRUD updates for filtered elements" in {
//
//      Given("Some data publisher")
//      val people = Publishers.of(
//        Person(1, "Georgina"),
//        Person(2, "Eleanor"),
//        Person(3, "Jayne"),
//        Person(1, "George")
//      )
//
//      When("we subscribe to the data w/ a DaoProcessor")
//      val dao = DaoProcessor[Int, Person]()
//      people.subscribe(dao)
//
//      And("observe the data through the DaoProcessor")
//      val crudListener = new ListSubscriber[CrudOperation[Int, Person]]
//      dao.subscribeWith(Set(1, 3), crudListener)
//
//      crudListener.receivedInOrderReceived() shouldBe Nil
//
//      Then("We should see Create values for the data requested")
//      crudListener.request(1)
//      eventually {
//        crudListener.receivedInOrderReceived() shouldBe List(Create(1, Person(1, "Georgina")))
//      }
//
//      // sanity check that we're only getting the first requested element
//      crudListener.request(1)
//      Thread.sleep(testNegativeTimeout.toMillis)
//      crudListener.receivedInOrderReceived() shouldBe List(Create(1, Person(1, "Georgina")))
//
//      // request the next element (Jayne, #3)
//      crudListener.request(1)
//      Thread.sleep(testNegativeTimeout.toMillis)
//      crudListener.receivedInOrderReceived() shouldBe List(Create(1, Person(1, "Georgina")), Create(3, Person(3, "Jayne")))
//
//      When("The last element is requested, it should see an update")
//      // request the next element, an update to Georgina
//      crudListener.request(1)
//      Thread.sleep(testNegativeTimeout.toMillis)
//      crudListener.receivedInOrderReceived() should contain inOrder (
//        Create(1, Person(1, "Georgina")),
//        Create(3, Person(3, "Jayne")),
//        Update(1, Person(1, "George"))
//      )
//
//      And("complete as the upstream data source is complete")
//      eventually {
//        crudListener.isCompleted() shouldBe true
//      }
//    }
  }
}
