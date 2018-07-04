package streaming.api

import agora.io.{FromBytes, ToBytes}
import monix.execution.{Cancelable, Scheduler}
import monix.reactive.{Observable, Observer, Pipe}
import streaming.api.sockets.WebFrame

import scala.util.{Failure, Try}

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

  def handleTextFramesWith(f : Observable[String] => Observable[String])(implicit scheduler : Scheduler, fromEv : FromRemote =:= WebFrame, toEv : ToRemote =:= WebFrame): Cancelable = {
    val fromText = fromRemote.map { from =>
      fromEv(from).asText.getOrElse(sys.error("Received non-text frame"))
    }
    val replies: Observable[String] = f(fromText)
    replies.map[WebFrame](WebFrame.text).subscribe(toRemote.asInstanceOf[Observer[WebFrame]])
  }

//  def viaSocket(socket : Endpoint[WebFrame, WebFrame])(implicit fromBytes: FromBytes[FromRemote], toBytes : ToBytes[ToRemote]) : Unit = {
//    val (a: Observer[FromRemote], b) = Pipe.publishToOne[FromRemote].unicast
//
//    val inFromRemote: Observable[Try[FromRemote]] = socket.fromRemote.map { frame =>
//      frame.asBinary.map { bin =>
//        fromBytes.read(bin.array())
//      }.getOrElse(Failure(new Exception(s"web frame wasn't binary: $frame")))
//    }
//
//    inFromRemote.pipeThrough()
//  }
}

object Endpoint {

  def socket[In : FromBytes, Out : ToBytes](onMsg : In => Out) : Endpoint[WebFrame, WebFrame] = {

//    val replies: Observable[String] = endpoint.fromRemote.doOnComplete { () =>
//      logger.debug("from remote on complete")
//      serverReceivedOnComplete = true
//    }.map { frame =>
//      logger.debug(s"To Remote : " + frame)
//      val text = frame.asText.getOrElse("Received a non-text frame")
//      messagesReceivedByTheServer += text
//      s"echo: $text"
//    }
//    val all = "Hi - you're connected to an echo-bot" +: replies
//    all.map[WebFrame](WebFrame.text).subscribe(endpoint.toRemote)
    ???
  }

  def instance[From, To](implicit endpoint: Endpoint[From, To]): Endpoint[From, To] = endpoint

  def apply[From, To](toRemote: Observer[To], fromRemote: Observable[From]): Endpoint[From, To] = {
    new Endpoint[From, To](toRemote, fromRemote)
  }

  def replay[T](initial: Seq[T]): Endpoint[T, T] = {
    val (to, from) = Pipe.replay[T](initial).unicast
    Endpoint(to, from)
  }
}
