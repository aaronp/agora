package jabroni.rest

import akka.http.scaladsl.Http

import scala.concurrent.Future

/**
  * Represents a running service - something which can be returning from starting a service that contains both
  * the binding and the config/service which was started
  */
case class RunningService[C <: ServerConfig, Service](conf: C, service: Service, binding: Http.ServerBinding) extends AutoCloseable {
  def stop(): Future[Unit] = binding.unbind()

  override def close(): Unit = stop()
}