package lupin.pub.query

import lupin.example.Accessor
import lupin.{BaseFlowSpec, ListSubscriber, Publishers}

class DaoProcessorTest extends BaseFlowSpec {

  "DaoProcessor" should {
    "publish CRUD updates for filtered elements" in {

      case class Person(id: Int, name: String)
      implicit object PersonIdAccessor extends Accessor[Person] {
        override type Index = Int

        override def get(value: Person): Index = value.id
      }
      val dao = DaoProcessor[Int, Person](Set.empty)
      val people = Publishers.of(
        Person(1, "Georgina"),
        Person(2, "Eleanor"),
        Person(3, "Jayne"),
        Person(1, "George")
      )
      people.subscribe(dao)

      val crudListener = new ListSubscriber[DaoProcessor.CrudOperation[Int, Person]]
      dao.subscribeWith(Set(1, 3), crudListener)

      crudListener.receivedInOrderReceived() shouldBe Nil
      crudListener.request(1)

      eventually {
        crudListener.receivedInOrderReceived() shouldBe List(DaoProcessor.Create(1, Person(1, "Georgina")))
      }

      // request the next element (Eleanor, #2)
      crudListener.request(1)
      Thread.sleep(testNegativeTimeout.toMillis)
      crudListener.receivedInOrderReceived() shouldBe List(DaoProcessor.Create(1, Person(1, "Georgina")))

      // request the next element (Jayne, #3)
      crudListener.request(1)
      Thread.sleep(testNegativeTimeout.toMillis)
      crudListener.receivedInOrderReceived() shouldBe List(DaoProcessor.Create(1, Person(1, "Georgina")), DaoProcessor.Create(3, Person(3, "Jayne")))

      // request the next element, an update to Georgina
      crudListener.request(1)
      Thread.sleep(testNegativeTimeout.toMillis)
      crudListener.receivedInOrderReceived() should contain inOrder(
        DaoProcessor.Create(1, Person(1, "Georgina")),
        DaoProcessor.Create(3, Person(3, "Jayne")),
        DaoProcessor.Update(1, Person(1, "George"))
      )
    }
  }
}
