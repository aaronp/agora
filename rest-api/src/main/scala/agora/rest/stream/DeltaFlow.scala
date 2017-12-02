package agora.rest.stream

/**
  * we're going for a dynamically updating table view for a data feed.
  *
  * The flow would be:
  *
  * 1) raw json data in =>
  * 2) a router using a list of jpaths to values to route the data (login messages go here, order messages go there...)
  *    if we assume this is done before the flow starts, then skip this step and assume the data at nr.1 is homogeneous
  * 3) like #2, but as an indexer index messages of the same id.
  *    There may be a bunch of each of these for each property we wanna index.

  * 4) Like #3, but an ordering one to keep track of all the values (min to max) for a particular field
  *
  * 5) a paths queue to track all the different paths available so we know how we might wanna set up the previous views.
  *
  */
object DeltaFlow {}
