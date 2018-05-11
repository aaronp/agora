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
