package agora.rest.client

import agora.api.worker.HostLocation

import scala.concurrent.{ExecutionContext, Future}

/**
  * Keeps track of [[RestClient]]s by their [[HostLocation]], which are created by the provided 'create' factory method
  * @param create
  */
case class CachedClient(create: HostLocation => RestClient) extends AutoCloseable {

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

  override def toString = {
    s"CachedClients: ${byLocation.keySet.map(_.asURL).toList.sorted.mkString("]", ",", "]")}"
  }

  override def close(): Unit = stop()

  def stop(): Future[Unit] = Lock.synchronized {
    val futures = byLocation.values.map(_.stop)
    byLocation = Map.empty
    import ExecutionContext.Implicits.global
    Future.sequence(futures).map(_ => Unit)
  }
}
