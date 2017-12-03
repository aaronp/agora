package agora.rest.stream

import agora.api.json.{JType, TypeNode}
import agora.api.streams.DataDiff.StrippedJsonDiff
import agora.api.streams._
import io.circe.Json
import org.reactivestreams.Publisher

import scala.util.Try

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

  /**
    * #5 path feed
    */
  trait FieldFeed {
    def fields: Vector[(List[String], JType)]

    def onNewFields(callback: Vector[(List[String], JType)] => Unit): Unit
  }

  object FieldFeed {

    import PublisherOps.implicits._

    class JsonFeed() extends FieldFeed {
      type Callback = Vector[(List[String], JType)] => Unit

      @volatile private var latestFields = Vector[(List[String], JType)]()
      private var callbacks              = Vector[Callback]()

      private val deltaSubscriber = new BaseSubscriber[Json](0) {
        override def onNext(delta: Json) = {
          val deltaPaths: Vector[(List[String], JType)] = TypeNode(delta).flattenPaths
          val newPaths                                  = deltaPaths.filterNot(latestFields.contains)
          if (newPaths.nonEmpty) {
            latestFields = latestFields ++ newPaths
            callbacks.foreach { cb =>
              Try(cb(newPaths))
            }
          }
        }
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

      override def fields: Vector[(List[String], JType)] = {
        latestFields
      }

      override def onNewFields(callback: Vector[(List[String], JType)] => Unit): Unit = {
        callbacks = callback +: callbacks
      }
    }

  }

}
