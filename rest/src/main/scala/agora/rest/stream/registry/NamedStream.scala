package agora.rest.stream.registry

import io.circe.Json
import org.reactivestreams.{Publisher, Subscriber}

case class NamedStream(val name: String,
                       publisherOpt: Option[Publisher[Json]],
                       subscribersByName: Map[String, Subscriber[Json]]) {


}
