package jabroni.rest.client

import jabroni.api.worker.HostLocation

case class CachedClient(create: HostLocation => RestClient) {

  private var byLocation = Map[HostLocation, RestClient]()

  private object Lock

  def remove(location: HostLocation) = Lock.synchronized {
    byLocation - location
  }

  def apply(location: HostLocation) = Lock.synchronized {
    byLocation.get(location) match {
      case Some(cached) => cached
      case None =>
        val c = create(location)
        byLocation = byLocation.updated(location, c)
        c
    }
  }

}
