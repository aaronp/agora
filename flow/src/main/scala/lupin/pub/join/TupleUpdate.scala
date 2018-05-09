package lupin.pub.join

sealed trait TupleUpdate[A, B] {
  def leftOption: Option[A]

  def rightOption: Option[B]
}

object TupleUpdate {
  def left[A, B](value: A): TupleUpdate[A, B] = LeftUpdate(value)

  def right[A, B](value: B): TupleUpdate[A, B] = RightUpdate(value)
}

case class BothUpdated[A, B](left: A, right: B) extends TupleUpdate[A, B] {
  override def leftOption = Option(left)

  override def rightOption = Option(right)
}

case class RightUpdate[A, B](right: B) extends TupleUpdate[A, B] {
  override val leftOption = None

  override def rightOption: Option[B] = Option(right)

  def and(a: A): BothUpdated[A, B] = BothUpdated(a, right)
}

case class LeftUpdate[A, B](left: A) extends TupleUpdate[A, B] {
  override val leftOption = Option(left)

  override def rightOption = None

  def and(b: B): BothUpdated[A, B] = BothUpdated(left, b)
}
