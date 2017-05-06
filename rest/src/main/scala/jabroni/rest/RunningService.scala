package jabroni.rest

import akka.http.scaladsl.Http

/**
  * Represents a running services
  *
  * @param conf
  * @param binding
  */
case class RunningService[C <: ServerConfig, Service](conf: C, service: Service, binding: Http.ServerBinding) {
  def stop() = binding.unbind()
}