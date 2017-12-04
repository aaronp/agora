package layout

import scala.compat.Platform

case class Point(x: Int, y: Int)

case class Line(x1: Int, y1: Int, x2: Int, y2: Int) {
  lazy val slope = (y2 - y1).toDouble / (x2 - x1).toDouble

  lazy val yIntersept: Double = y1 - (x1 * slope)

  def xAtY(y: Double) = (y - yIntersept) / slope

  def yAtX(x: Double) = (x * slope) + yIntersept

  def width = (x2 - x1).abs

  def height = (y2 - y1).abs

  def containingRectangle = {
    val x = y1.min(y2)
    val y = x1.min(x2)
    Rectangle(x, y, width, height)
  }

}

case class Rectangle(x: Int, y: Int, width: Int, height: Int) {

  def x2 = x + width

  def y2 = y + height

  def contains(point: Point): Boolean = contains(point.x, point.y)

  def containsX(px: Int): Boolean = {
    val diff = px - x
    diff >= 0 && diff <= width
  }

  def containsY(py: Int): Boolean = {
    val diff = py - y
    diff >= 0 && diff <= height
  }

  def contains(px: Int, py: Int): Boolean = containsX(px) && containsY(py)

  def translate(deltaX: Int = 0, deltaY: Int = 0): Rectangle = {
    if (deltaX == 0 && deltaY == 0) {
      this
    } else {
      copy(x + deltaX, y + deltaY)
    }
  }

  def scale(deltaX: Double = 1.0, deltaY: Double = 1.0): Rectangle = {
    val newW = width * deltaX
    val newH = height * deltaY
    val newX = (x * deltaX * -1) / 2
    val newY = (y * deltaY * -1) / 2
    Rectangle(newX.toInt, newY.toInt, newW.toInt, newH.toInt)
  }

  def toOrigin: Rectangle = translate(-x, -y)

}

case class Scrollbar(scene: Rectangle, view: Rectangle) {
  val widthPercent = {
    if (scene.width == 0) {
      100.0
    } else {
      view.width.toDouble / scene.width
    }
  }
  val heightPercent = {
    if (scene.height == 0) {
      100.0
    } else {
      view.height.toDouble / scene.height
    }
  }
  val leftOffset  = (view.x - scene.x) * widthPercent
  val rightOffset = (view.y - scene.y) * heightPercent

}

case class AsciiRow(charsAtX: List[(Int, Char)] = Nil) {
  def x1 = charsAtX.headOption.map(_._1).getOrElse(0)

  def width = charsAtX.lastOption.map(_._1).getOrElse(0)

  def render(minX: Int, space: Char = ' '): String = {
    val maxX = width
    val from = 0
    val row = charsAtX.zip((from to maxX)).map {
      case ((x, char), i) if x == i => char
      case _                        => space
    }
    row.mkString("")
  }

  def at(x: Int) = charsAtX.collectFirst {
    case (`x`, row) => row
  }

  def merge(other: AsciiRow, intersectChar: Option[Char]): AsciiRow = {
    val firstRowX = (x1.min(other.x1))
    val maxWidth  = (width.max(other.width))

    val newChars = (firstRowX to maxWidth).flatMap { x =>
      val opt = (at(x), other.at(x)) match {
        case (Some(_), rhs @ Some(_)) => intersectChar.orElse(rhs) // rhs wins over lhs
        case (None, rhs)              => rhs
        case (lhs, None)              => lhs
      }
      opt.map(x -> _)
    }
    AsciiRow(newChars.toList)
  }
}

case class AsciiRows(rows: List[(Int, AsciiRow)] = Nil) {
  def width = rows.map(_._2.width).reduceOption(_ max _).getOrElse(0)

  def height = rows.lastOption.map(_._1).getOrElse(0)

  def y1 = rows.headOption.map(_._1).getOrElse(0)

  def minX = rows.map(_._2.x1).reduceOption(_ min _).getOrElse(0)

  def render(space: Char = ' ') = {
    val cachedMinX   = minX
    val cachedHeight = height
    val lines = rows.zip((y1 to cachedHeight)).map {
      case ((rowY, row), i) if i == rowY => row.render(cachedMinX, space)
      case _                             => ""
    }
    lines.mkString(Platform.EOL)
  }

  def at(y: Int) = rows.collectFirst {
    case (`y`, row) => row
  }

  def merge(other: AsciiRows, intersectChar: Option[Char] = None): AsciiRows = {
    val firstRowY = (y1.min(other.y1))
    val maxHeight = (height.max(other.height))

    val newRows = (firstRowY to maxHeight).flatMap { y =>
      val opt = (at(y), other.at(y)) match {
        case (Some(lhs), Some(rhs)) => Some(lhs.merge(rhs, intersectChar))
        case (lhs, None)            => lhs
        case (None, rhs)            => rhs
      }
      opt.map(y -> _)
    }
    AsciiRows(newRows.toList)
  }
}

trait Layout[T] {
  type UI

  def layout(value: T, view: Rectangle): UI
}

object Layout {

  type Aux[T, UI2] = Layout[T] { type UI = UI2 }

  implicit def auxAsLayout[T, UI2](implicit aux: Aux[T, UI2]): Layout[T] { type UI = UI2 } = {
    aux
  }

  object syntax {
    implicit def richValue[T](value: T) = new {
      def layout[UI](view: Rectangle)(implicit aux: Aux[T, UI]): UI = {
        aux.layout(value, view)
      }
    }
  }

  type AsciiLayout[T] = Aux[T, AsciiRows]

  implicit object AsciiLine extends Layout[Line] {
    type UI = AsciiRows

    override def layout(value: Line, view: Rectangle): AsciiRows = {

      val lineBounds = value.containingRectangle

      val rows = for {
        y <- lineBounds.y to lineBounds.y2
        if view.containsY(y)
      } yield {
        val xValue = value.xAtY(y).toInt
        val row = if (view.containsX(xValue)) {
          AsciiRow(List(xValue -> 'o'))
        } else {
          AsciiRow()
        }
        y -> row
      }

      AsciiRows(rows.toList)
    }
  }

}
