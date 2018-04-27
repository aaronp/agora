package lupin.pub.impl

/**
  * A means for naming things. The idea is that various implementations can elect
  * to implement this trait and then use it in debug information, toString implementations, etc
  *
  */
trait HasName {

  /** @return the name of this thing
    */
  def name: String
}
