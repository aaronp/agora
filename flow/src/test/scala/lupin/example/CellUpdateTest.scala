package lupin.example

import lupin.BaseFlowSpec
import monix.eval.Task
import monix.execution.Scheduler.Implicits.global
import monix.reactive.Observable
import monix.reactive.subjects.ConcurrentSubject
import org.scalatest.GivenWhenThen

class CellUpdateTest extends BaseFlowSpec with GivenWhenThen {

  "CellUpdate flow" should {
    "expose the fields in a view from a static publisher" in {
      case class Person(id: Int, name: String, lastName: String, favouriteColour: String)

      Given("Some data source")
      // create some data source
      val pub: Observable[Person] = Observable(
        Person(1, "dave", "smith", "yellow"),
        Person(2, "fizz", "bar", "green"),
        Person(3, "foo", "bizz", "green"),
        Person(4, "foo", "buzz", "black"),
        Person(5, "sue", "smith", "red"),
        Person(6, "kevin", "arnold", "green")
      )

      And("A ViewPort feed")
      val viewUpdates                        = ConcurrentSubject.publishToOne[ViewPort]
      val viewUpdates1: Observable[ViewPort] = viewUpdates // Observable[ViewPort]()

      When("the two are joined in a table view")
      val tables: RawFeed[TableView[Int, RenderableFieldUpdate[Int, Person]]] =
        TableView.subscribeTo[Person, Int, RenderableFieldUpdate[Int, Person]](pub.toReactivePublisher, viewUpdates.toReactivePublisher)

      Then("we should be able to observe the data flowing through the data source via the view port")

      // now create a table view based on the data and views
      //      val updates = new ListSubscriber[TableView[_, _]]
      def updates(): List[TableView[Int, RenderableFieldUpdate[Int, Person]]] = {
        val values: Task[List[TableView[Int, RenderableFieldUpdate[Int, Person]]]] = Observable.fromReactivePublisher(tables).toListL
        val list                                                                   = values.runAsync.futureValue
        list
      }

      // send our first view update
      val firstView = ViewPort(1, 2, SortCriteria("name"), List("lastName", "id"))
      //val p: ConnectableObservable[ViewPort] = viewUpdates.publish

      //val table =
      eventually {
        val List(firstTable) = updates()
        //        firstTable.view shouldBe firstView
        //        firstTable
      }
      //      table.render() shouldBe
      //        """
      //          >+------+---+
      //          >| fizz | 2 |
      //          >+------+---+
      //          >| foo  | 3 |
      //          >+------+---+
      //        """.stripMargin('>')

    }
  }

}
