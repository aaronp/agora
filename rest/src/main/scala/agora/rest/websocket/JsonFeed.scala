package agora.rest.websocket

import cats.Functor

/**
  * This represents a ticking feed of data (represented in json) which
  * will eventually result in a 'realtime' view on some downstream
  * dashboard.
  *
  * There may be several instances of this flow (e.g. for sharding the work), which will be
  * collated by downstream views.
  *
  * A single feed has the flow:
  *
  *
  * -- RDF --- raw data reader :: group the data by key, publish data deltas ----------
  *
  * 1) raw data in
  * 2) a key selector routes it into a bucket by some 'key'
  * 3) the data view is updated for that feed to produce a delta and notify downstream components
  *
  * ---- MinMax feed :: keep track of min/max values for a particular field ----
  *
  * Note: This should be managed in a single component, so we don't need to have multiple
  * instances all tracking the same field.
  *
  * 1) subscribe to RDF for each field we're interested in
  * 2) on SOW/Delta, update the min/max list and publish a position updates for whichever key field was specified*
  *
  * In a MinMax feed, we'll keep track of the entire values, but also track a field selector per downstream subscription
  * so they can see e.g. 'Delta( Insert(Position: 3, FieldValue : foo), Remote(Position: 1, FieldValue : bar))'
  * where the 'field value' is based on the provided selector(s)
  *
  * ----
  */
object JsonFeed {

  /**
    * A field selector is just a Functor instance, allowing a type T to 'map' onto a field:
    */
  object FieldSelector {}

}
