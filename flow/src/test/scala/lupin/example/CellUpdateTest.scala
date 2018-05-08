package lupin.example

import lupin.pub.sequenced.DurableProcessor
import lupin.{BaseFlowSpec, ListSubscriber, Publishers}
import org.scalatest.concurrent.Eventually

class CellUpdateTest extends BaseFlowSpec {


  "CellUpdate flow" should {
    "expose the fields in a view from a static publisher" in {
      case class Person(id: Int, name: String, lastName: String, favouriteColour: String)

      val people = List(
        Person(1, "dave", "smith", "yellow"),
        Person(2, "fizz", "bar", "green"),
        Person(3, "foo", "bizz", "green"),
        Person(4, "foo", "buzz", "black"),
        Person(5, "sue", "smith", "red"),
        Person(6, "kevin", "arnold", "green")
      )

      // create some data source
      val pub = Publishers.forList(people)

      // an a view source
      val viewUpdates = DurableProcessor[ViewPort]()

      val tables = TableView.subscribeTo(pub, viewUpdates)

      // now create a table view based on the data and views
      val updates = new ListSubscriber[TableView[_, _]]
      tables.subscribe(updates)

      // send our first view update
      val firstView = ViewPort(1, 2, SortCriteria("name"), List("lastName", "id"))
      viewUpdates.onNext(firstView)

      //val table =
        eventually {
        val List(firstTable) = updates.received()
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
