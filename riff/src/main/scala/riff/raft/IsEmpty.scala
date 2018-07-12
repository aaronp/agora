package riff.raft

import simulacrum.typeclass

@typeclass trait IsEmpty[T] {
  def isEmpty(value : T) : Boolean

  def nonEmpty(value : T) : Boolean = !isEmpty(value)

  def empty : T
}

object IsEmpty {
  implicit object StringIsEmpty extends IsEmpty[String] {
    override def isEmpty(value: String): Boolean = value.isEmpty
    override def empty: String = ""
  }
  implicit def asArrayIsEmpty[T] = new IsEmpty[Array[T]] {
    override def isEmpty(value: Array[T]): Boolean = value.isEmpty
    override def empty: Array[T] = Array.empty[T]
  }
  implicit def asSeqIsEmpty[T] = new IsEmpty[Seq[T]] {
    override def isEmpty(value: Seq[T]): Boolean = value.isEmpty
    override def empty: Seq[T] = Seq.empty[T]
  }
}