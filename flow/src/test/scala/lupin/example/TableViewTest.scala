package lupin.example

import lupin.BaseFlowSpec

class TableViewTest extends BaseFlowSpec {

  type RFU = RenderableFieldUpdate[String, String]

  def newUpdate(): CellUpdate[String, RFU] = {
    CellUpdate[String, RenderableFieldUpdate[String, String]](None, 0, Map.empty)
  }

  "TableView.render" should {
    "render an empty view" in {
      val view = TableView[String, RFU](ViewPort(50, 52, SortCriteria("foo")), newUpdate())
      view.render() shouldBe
        """
          >++
          >||
          >++
          >||
          >++
          >||
          >++
          >||
          >++
        >""".stripMargin('>')
    }
    "render a view with columns but no data" in {
      val view = TableView[String, RFU](ViewPort(0, 3, SortCriteria("foo"), List("id", "second column", "bar")), newUpdate())
      view.render() shouldBe
        """
          >+-----+---------------+-----+
         >| id  | second column | bar |
         >+-----+---------------+-----+
         >|  -  |  -            |  -  |
         >+-----+---------------+-----+
         >|  -  |  -            |  -  |
         >+-----+---------------+-----+
         >|  -  |  -            |  -  |
         >+-----+---------------+-----+
         >|  -  |  -            |  -  |
         >+-----+---------------+-----+
         >""".stripMargin('>')
    }
    "render only the contents in the view" in {
      val map = newUpdate.updated(
        CellCoord(1, "id")      -> newValue(1, "a"),
        CellCoord(1, "foo")     -> newValue(1, "bar"),
        CellCoord(1, "ignored") -> newValue(1, "meh"),
        CellCoord(2, "id")      -> newValue(2, "second"),
        CellCoord(3, "id")      -> newValue(3, "third"),
        CellCoord(4, "meh")     -> newValue(4, "some value"),
        CellCoord(5, "id")      -> newValue(5, "out of scope")
      )
      val view = TableView[String, RFU](ViewPort(1, 4, SortCriteria("foo"), List("foo", "id", "meh", "doesn't appear")), map)
      view.render() shouldBe
        """
          >+-----+--------+------------+----------------+
          >| foo | id     | meh        | doesn't appear |
          >+-----+--------+------------+----------------+
          >| bar | a      |  -         |  -             |
          >+-----+--------+------------+----------------+
          >|  -  | second |  -         |  -             |
          >+-----+--------+------------+----------------+
          >|  -  | third  |  -         |  -             |
          >+-----+--------+------------+----------------+
          >|  -  |  -     | some value |  -             |
          >+-----+--------+------------+----------------+
        >""".stripMargin('>')

    }
  }

  def newValue(idx: Long, value: String) = RenderableFieldUpdate.create[String, String](
    id = s"ID-$idx",
    seqNo = 0,
    index = idx,
    value = value
  )

}
