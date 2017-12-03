package agora.rest.stream

import agora.api.json.{TypesByPath, _}
import agora.api.streams.DataDiff.StrippedJsonDiff
import agora.api.streams._
import io.circe.Json
import org.reactivestreams.Publisher

/**
  * we're going for a dynamically updating table view for a data feed.
  *
  * The flow would be:
  *
  * 1) raw json data in =>
  * 2) a router using a list of jpaths to values to route the data (login messages go here, order messages go there...)
  * if we assume this is done before the flow starts, then skip this step and assume the data at nr.1 is homogeneous
  * 3) like #2, but as an indexer index messages of the same id.
  * There may be a bunch of each of these for each property we wanna index.
  *
  * 4) Like #3, but an ordering one to keep track of all the values (min to max) for a particular field
  *
  * 5) a paths queue to track all the different paths available so we know how we might wanna set up the previous views.
  *
  */
object DeltaFlow {

  trait Labels {
    def labelFor(path: List[String]): String

    def pathForLabel(label: String): Option[List[String]]
  }

  object Labels {
    def apply(initialMap: Map[List[String], String] = Map()) = new Buffer(initialMap)

    class Buffer(initialMap: Map[List[String], String] = Map()) extends Labels {
      private var labelsByPath = initialMap
      def setLabel(path: List[String], label: String) = {
        labelsByPath = labelsByPath.updated(path, label)
      }

      def default(path: List[String]) = path.map(_.capitalize).mkString(" ")

      override def labelFor(path: List[String]): String = labelsByPath.getOrElse(path, default(path))

      override def pathForLabel(label: String): Option[List[String]] = {
        val found = labelsByPath.collectFirst {
          case (key, value) if value == label => key
        }
        found
      }
    }
  }

  /**
    * #5 path feed
    */
  trait FieldFeed {
    def fields: TypesByPath
  }

  object FieldFeed {
    type Callback = TypesByPath => Unit

    import PublisherOps.implicits._

    def apply(callback: TypesByPath => Unit) = new JsonFeed(callback)

    /**
      * This feed expects json messages from a publisher which sends small snippets
      * (via StrippedJsonDiff)
      * @param callback
      */
    class JsonFeed(callback: TypesByPath => Unit) extends FieldFeed {

      private val deltaSubscriber = new AccumulatingSubscriber[TypesByPath, Json](0, newTypesByPath()) {
        override protected def combine(lastState: TypesByPath, delta: Json) = {
          val deltaPaths = TypeNode(delta).flattenPaths
          val newPaths   = deltaPaths.filterNot(lastState.contains)
          if (newPaths.nonEmpty) {
            callback(newPaths)
            lastState ++ newPaths
          } else {
            lastState
          }
        }
        def fields = state
      }

      def connect(publisher: Publisher[Json], initialRequest: Long = 1L) = {
        implicit val jsonDelta = StrippedJsonDiff
        publisher.subscribeToUpdates(deltaSubscriber, initialRequest)
      }

      lazy val myPublisher = {
        val p = BasePublisher[Json](100)
        connect(p)
        p
      }

      override def fields = deltaSubscriber.fields
    }

  }

}
