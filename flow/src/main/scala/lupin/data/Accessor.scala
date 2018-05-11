package lupin.data

/**
  * This will allow us to lookup a property for a given type T.
  *
  * Could be thought of as just the accessor part of a Lens
  *
  * we might have a
  * {{{
  *   val getId : Accessor[Thing, Long] = ???
  *   val getName : Accessor[Thing, String] = ???
  *   val id : Long = getId.indexOf(someThing)
  * }}}
  *
  * @tparam T
  */
trait Accessor[T, A] {
  def get(value: T): A
}

object Accessor {
  trait Aux[T] {
    type Prop
    def get(value : T) : Prop
  }

  implicit def auxAsAccessor[T](aux : Aux[T]) : Accessor[T, aux.Prop] = new Accessor[T, aux.Prop] {
    override def get(value: T): aux.Prop = aux.get(value)
  }

}