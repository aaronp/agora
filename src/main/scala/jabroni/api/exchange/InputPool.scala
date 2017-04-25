package jabroni.api.exchange

/**
  * Stores inputs to the exchange
  *
  */
trait InputPool[ID, T] {

  def all : Iterator[(ID, T)]

  def find(f: (ID, T) => Boolean): Iterator[(ID, T)] = all.filter {
    case (id, t) => f(id, t)
  }

  def push(id : ID, value : T, inProgress : Boolean)

  // clears any 'mark' calls
  def clear(id: ID): Unit

  def markInProgress(id: ID): Unit

  def markComplete(id: ID): Unit

}
