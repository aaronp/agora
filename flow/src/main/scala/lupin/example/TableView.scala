package lupin.example

import lupin.Publishers
import org.reactivestreams.Publisher

import scala.collection.immutable
import scala.concurrent.ExecutionContext

/**
  * Finally, we can join the ViewFeed with a CellFeed to render a sorted view (or otherwise) of data updates
  *
  * We could also feed downstream processors to e.g. sort on secondary indices which would have to re-order
  * indices based on adjacent similar values
  */
case class TableView[ID, U <: RenderableFieldUpdate[ID, _]](view: ViewPort, cells: CellUpdate[ID, U]) {
  def update(newView: ViewPort) = {
    val newCells = cells.filter { cell =>
      newView.indices.contains(cell.index) && newView.cols.contains(cell.column)
    }
    copy(newView, newCells)
  }

  def update(newValue: CellUpdate[ID, U]) = {
    val newCells = cells.merge(newValue).filter { cell =>
      view.indices.contains(cell.index) && view.cols.contains(cell.column)
    }
    copy(view, newCells)
  }

  def render(): String = {
    def rowAtIndex(index: Long) = {
      view.cols.map { col =>
        val cellValueOpt = cells.updates.get(CellCoord(index, col)).map { fieldUpdate =>
          fieldUpdate.render
        }
        cellValueOpt.getOrElse(" - ")
      }
    }

    val grid: immutable.Seq[List[String]] = view.indices match {
      case IndexRange(from, to) =>
        from.to(to).map(rowAtIndex)
      case SpecificIndices(indices) =>
        indices.map(rowAtIndex)
    }
    val titledGrid: immutable.Seq[List[String]] = view.cols +: grid
    val colSizes: immutable.Seq[immutable.Seq[Int]] = titledGrid.transpose.map(_.map(_.length))
    val maxColSizes: immutable.Seq[Int] = colSizes.map(_.max)

    val rows: immutable.Seq[String] = titledGrid.map { row =>
      row
        .zip(maxColSizes)
        .map {
          case (text, width) => s" ${text.padTo(width, ' ')} "
        }
        .mkString("|", "|", "|")
    }

    val bar = maxColSizes
      .map { len =>
        "-" * (len + 2)
      }
      .mkString("+", "+", "+")
    rows.mkString(s"\n$bar\n", s"\n$bar\n", s"\n$bar\n")
  }
}

object TableView {
  def subscribeTo[T, ID, U <: RenderableFieldUpdate[ID, _]](data: Publisher[T], views: Publisher[ViewPort])(implicit ec: ExecutionContext): Publisher[TableView[ID, U]] = {

    val cells: CellFeed[ID, U] = CellUpdate.subscribeTo[T, ID, U](data, views)
    Publishers.map(cells) { cellUpdate: CellUpdate[ID, U] =>
      //cellUpdate
      ???
    }
    ???
  }
}