package agora.rest.stream.registry

import io.circe.Json
import org.reactivestreams.Publisher

/**
  * Datastructure which backs the Stream routes, allowing publishers/subscribers to connect against a name.
  *
  * Its publishers/subscribers will ultimately back a websocket flow.
  *
  * The trick for those flows is that akka websocket sources have their own publishers, and will 'request' more
  * messages as soon as they are delivered.
  */
class StreamRegistry(initialStreamsByName: Map[String, NamedStream[_]] = Map.empty) {

  private var streamsByName = initialStreamsByName

  def registerPublisher(key: String, publisher: Publisher[Json]): Boolean = {
    false
  }

}

