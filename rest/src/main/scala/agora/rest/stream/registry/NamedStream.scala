package agora.rest.stream.registry

import agora.rest.stream.DataConsumerFlow
import io.circe.Json
import org.reactivestreams.{Publisher, Subscriber}

case class NamedStream[T](val name: String,
                       publisherOpt: Option[Publisher[T]],
                       subscribers: List[DataConsumerFlow[T]]) {


}
