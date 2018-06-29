package streaming.api

import monix.reactive.{Observable, Observer, Pipe}

/**
  * Represents a connection to another system, either local or remote.
  *
  * Just a specialised kind of Pipe really which has typed inputs/outputs.
  *
  * @param fromRemote
  * @param toRemote
  * @tparam FromRemote
  * @tparam ToRemote
  */
class Endpoint[FromRemote, ToRemote](val fromRemote: Observable[FromRemote], val toRemote: Observer[ToRemote]) {
  def via(other: Endpoint[ToRemote, ToRemote])(implicit ev: FromRemote =:= ToRemote) = {
    Endpoint.via[ToRemote](map(ev), other)
  }

  final def map[A](f: FromRemote => A): Endpoint[A, ToRemote] = new Endpoint[A, ToRemote](fromRemote.map(f), toRemote)
}

object Endpoint {
  def apply[From, To](implicit endpoint: Endpoint[From, To]): Endpoint[From, To] = endpoint

  def apply[From, To](fromRemote: Observable[From], toRemote: Observer[To]): Endpoint[From, To] = {
    new Endpoint[From, To](fromRemote, toRemote)
  }

  def via[T](lhs: Endpoint[T, T], rhs: Endpoint[T, T]): Endpoint[T, T] = {
    val p1 = Pipe.replay(List[String]())
    val p2 = Pipe.replay(List[String]())

    p1.unicast

  }
}
