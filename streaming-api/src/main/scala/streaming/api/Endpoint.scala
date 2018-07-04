package streaming.api

import agora.io.{FromBytes, ToBytes}
import monix.reactive.{Observable, Observer, Pipe}

/**
  * Represents a connection to another system, either local or remote.
  *
  * Just a specialised kind of Pipe really which has typed inputs/outputs.
  *
  * @param toRemote an observer which can be attached to an Observable of events which should be sent to the endpoint
  * @param fromRemote an observable of messages coming from the endpoint
  * @tparam FromRemote
  * @tparam ToRemote
  */
class Endpoint[FromRemote, ToRemote](val toRemote: Observer[ToRemote], val fromRemote: Observable[FromRemote]) {
  final def map[A](f: FromRemote => A): Endpoint[A, ToRemote] = new Endpoint[A, ToRemote](toRemote, fromRemote.map(f))
  final def contraMap[A](f: A => ToRemote): Endpoint[FromRemote, A] = {
    import streaming.api.implicits._
    new Endpoint[FromRemote, A](toRemote.contraMap(f), fromRemote)
  }
}

object Endpoint {
  def instance[From, To](implicit endpoint: Endpoint[From, To]): Endpoint[From, To] = endpoint

  def apply[From, To](toRemote: Observer[To], fromRemote: Observable[From]): Endpoint[From, To] = {
    new Endpoint[From, To](toRemote, fromRemote)
  }

  def replay[T](initial: Seq[T]): Endpoint[T, T] = {
    val (to, from) = Pipe.replay[T](initial).unicast
    Endpoint(to, from)
  }

  trait Socket extends Endpoint[WebFrame, WebFrame] {
    def viaBytes[In: ToBytes, Out: FromBytes]() = {


    }
    def viaText[In: ToBytes, Out: FromBytes]() = {

    }
  }
}
