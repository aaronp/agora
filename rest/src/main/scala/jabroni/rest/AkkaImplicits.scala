package jabroni.rest

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer

class AkkaImplicits(actorSystemName: String) {
  println(s"Starting actor system for $actorSystemName")
  implicit val system = ActorSystem(actorSystemName)
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher
  implicit val http = Http()
}
