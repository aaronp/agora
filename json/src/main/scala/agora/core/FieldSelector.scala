package agora.core

/**
  * The ability to choose a particular field B from an input value of type A
  *
  * Like a Lens, but w/ only the 'get' accessor
  *
  * @tparam A the input type
  * @tparam B the selected field type (which may be an Option)
  */
trait FieldSelector[A, B] {

  /** @param in the input field
    * @return the value for the given field
    */
  def select(in: A): B
}

object FieldSelector {

  def lift[A, B](f: A => B): FieldSelector[A, B] = {
    new FieldSelector[A, B] {
      override def select(in: A) = f(in)
    }
  }
}
