package lupin.example

/**
  * This will allow us to lookup a property for a given type T.
  *
  * Could be thought of as just the accessor part of a Lens
  *
  * we might have a
  * {{{
  *   val getId : Accessor.Aux[Thing, Long] = ???
  *   val getName : Accessor.Aux[Thing, String] = ???
  *   val id : Long = getId.indexOf(someThing)
  * }}}
  *
  * @tparam T
  */
trait Accessor[T] {
  type Index

  def get(value: T): Index
}

object Accessor {

  type Aux[T, I] = Accessor[T] { type Index = I }
}
