package streaming.api

import monix.reactive.{Observable, Observer}

class Endpoint[FromRemote, ToRemote](val fromRemote: Observable[FromRemote], val toRemote: Observer[ToRemote])

object Endpoint {
  def apply[From, To](implicit endpoint: Endpoint[From, To]): Endpoint[From, To] = endpoint

  def apply[From, To](fromRemote: Observable[From], toRemote: Observer[To]): Endpoint[From, To] = {
    new Endpoint[From, To](fromRemote, toRemote)
  }
}
