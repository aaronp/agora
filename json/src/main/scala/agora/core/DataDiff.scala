package agora.core

/**
  * A 'DataDiff' can diff two instances of A and produce a delta of type D
  *
  * @tparam A the data type to diff
  * @tparam D the delta type
  */
trait DataDiff[A, D] {

  /**
    * Produce a difference D from two instances of A
    *
    * @param lhs the left-hand-side
    * @param rhs the right-hand-side
    * @return a difference D
    */
  def diff(lhs: A, rhs: A): D

  def map[D1](f: D => D1): DataDiff[A, D1] = {
    val self = this
    new DataDiff[A, D1] {
      override def diff(lhs: A, rhs: A) = f(self.diff(lhs, rhs))
    }
  }
}
