package finance.rest

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import com.typesafe.config.Config

trait BaseConfig {

  def config: Config

  object implicits {
    implicit val system = ActorSystem("finance")
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher
  }

}
