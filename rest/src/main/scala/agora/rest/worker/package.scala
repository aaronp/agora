package agora.rest

import akka.http.scaladsl.model.{HttpRequest, HttpResponse}

import scala.concurrent.Future

package object worker {

  type HttpHandler = HttpRequest => Future[HttpResponse]
}
