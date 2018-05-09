package lupin.pub.query

import lupin.data.Accessor
import lupin.{BaseFlowSpec, ListSubscriber, Publishers}

class DaoProcessorTest extends BaseFlowSpec {

  // create some example data type
  case class Person(id: Int, name: String)

  // .. and put in scope an accessor for obtaining an ID for our new type
  implicit object PersonIdAccessor extends Accessor[Person] {
    override type Index = Int

    override def get(value: Person): Index = value.id
  }

  "DaoProcessor" should {
    "publish initial elements meeting the criteria" in {

      val dao = DaoProcessor.apply[Int, Person]()

      val people = Publishers.of(
        Person(1, "Georgina"),
        Person(2, "Eleanor"),
        Person(3, "Jayne"),
        Person(1, "George")
      )
      people.subscribe(dao)
      val initialLoadListener = new ListSubscriber[CrudOperation[Int, Person]]
      dao.subscribe(initialLoadListener)
      initialLoadListener.request(10) // load up our DAO prior to any new subscriptions
      eventually {
        initialLoadListener.receivedInOrderReceived().size shouldBe 4
      }

      val crudListener = new ListSubscriber[CrudOperation[Int, Person]]
      dao.subscribeWith(Set(1, 3), crudListener)

      crudListener.request(5)
      eventually {
        crudListener.receivedInOrderReceived() should contain only (
          Create(1, Person(1, "George")),
          Create(3, Person(3, "Jayne"))
        )
      }

    }
    "publish CRUD updates for filtered elements" in {

      val dao = DaoProcessor[Int, Person]()
      val people = Publishers.of(
        Person(1, "Georgina"),
        Person(2, "Eleanor"),
        Person(3, "Jayne"),
        Person(1, "George")
      )
      people.subscribe(dao)

      val crudListener = new ListSubscriber[CrudOperation[Int, Person]]
      dao.subscribeWith(Set(1, 3), crudListener)

      crudListener.receivedInOrderReceived() shouldBe Nil
      crudListener.request(1)

      eventually {
        crudListener.receivedInOrderReceived() shouldBe List(Create(1, Person(1, "Georgina")))
      }

      // request the next element (Eleanor, #2)
      crudListener.request(1)
      Thread.sleep(testNegativeTimeout.toMillis)
      crudListener.receivedInOrderReceived() shouldBe List(Create(1, Person(1, "Georgina")))

      // request the next element (Jayne, #3)
      crudListener.request(1)
      Thread.sleep(testNegativeTimeout.toMillis)
      crudListener.receivedInOrderReceived() shouldBe List(Create(1, Person(1, "Georgina")), Create(3, Person(3, "Jayne")))

      // request the next element, an update to Georgina
      crudListener.request(1)
      Thread.sleep(testNegativeTimeout.toMillis)
      crudListener.receivedInOrderReceived() should contain inOrder (
        Create(1, Person(1, "Georgina")),
        Create(3, Person(3, "Jayne")),
        Update(1, Person(1, "George"))
      )
    }
  }
}
