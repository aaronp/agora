package agora.rest.stream

import com.typesafe.scalalogging.StrictLogging

import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.server.Directives.{extractMaterializer, handleWebSocketMessages, path, _}
import akka.http.scaladsl.server.Route
import com.typesafe.scalalogging.StrictLogging
import io.circe.Json
import io.circe.syntax._

/**
  * ======================== Use Case: Simple Ad-Hoc Streams ========================
  * We want to be able to:
  * (1) create a stream to publish to from a websocket
  * (2) subscribe to a created stream
  * (3) query the stream (throughput, number of messages, number of subscribers, etc)
  *
  * {{{
  *   POST rest/stream
  * }}}
  * ======================== Use Case: Attach Streams ========================
  * We want to be able to:
  * (1) Add a subscriber to an existing stream:
  * (1.a) it should use the local Flow if on this server (same HostLocation) or...
  * (1.b) a websocket if remote
  * (2) subscribe to a created stream
  * (3) query the stream (throughput, number of messages, number of subscribers, etc)
  *
  * ======================== Other Use Cases: Advanced Streams ========================
  * Once we have the above, we want to add custom streams for:
  * (1) indexing based on json paths and providing subscriptions for each of the indexed values (and queries of those values)
  * (2) conflating
  * (3) persistent/historic (keeping a numbered subscription and allowing subscriptions from arbitrary points) a la Kafka
  * (4) tracking available paths (via [[agora.json.TypeNode]])
  * (5) providing deltas
  * (6) coalescing multiple publishers into a single publisher
  * (7) providing calculated values based on [[agora.json.JExpression]]s
  * (8) .... ?
  *
  * ultimately the above will be able to support the goal of having a tabular view so we can have a dynamic viewport based on some
  * properties and an offset + pageSize
  *
  */
class JsonStreamRoutes extends StrictLogging {}
