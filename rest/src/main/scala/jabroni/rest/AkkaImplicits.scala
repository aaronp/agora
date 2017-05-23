package jabroni.rest

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.ActorMaterializer
import com.typesafe.scalalogging.StrictLogging

class AkkaImplicits(actorSystemName: String) {
  implicit val system = ActorSystem(actorSystemName)
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher
  implicit val http = Http()
}
