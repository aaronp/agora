package lupin.pub.join

sealed trait TupleUpdate[A, B] {
  def leftOption: Option[A]

  def rightOption: Option[B]
}

case class BothUpdated[A, B](left: A, right: B) extends TupleUpdate[A, B] {
  override def leftOption = Option(left)

  override def rightOption = Option(right)
}

case class RightUpdate[A, B](right: B) extends TupleUpdate[A, B] {
  override val leftOption = None

  override def rightOption: Option[B] = Option(right)
}

case class LeftUpdate[A, B](left: A) extends TupleUpdate[A, B] {
  override val leftOption = Option(left)

  override def rightOption = None
}

