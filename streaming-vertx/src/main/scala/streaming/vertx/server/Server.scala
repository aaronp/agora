package streaming.vertx.server

import com.typesafe.scalalogging.StrictLogging
import io.vertx.core.Handler
import io.vertx.lang.scala.ScalaVerticle
import io.vertx.scala.core.Vertx
import io.vertx.scala.core.http.{HttpServerRequest, ServerWebSocket}
import io.vertx.scala.ext.web.Router
import io.vertx.scala.ext.web.handler.StaticHandler
import monix.execution.Scheduler
import monix.reactive.Observable
import streaming.api.HostPort
import streaming.rest.RestRequestContext

import scala.concurrent.duration.Duration

object Server {

  type OnConnect = ServerEndpoint => Unit

  object LoggingHandler extends Handler[HttpServerRequest] with StrictLogging {
    override def handle(event: HttpServerRequest): Unit = {
      logger.info(s"Received $event")
    }
  }

  def startSocket(port: Int, nullableName: String = null)(onConnect: OnConnect)(implicit timeout: Duration, scheduler: Scheduler): ScalaVerticle = {
    val name = Option(nullableName).getOrElse("general")

    val websocketHandler = ServerWebSocketHandler.replay(name)(onConnect)
    start(HostPort.localhost(port), LoggingHandler, websocketHandler)
  }

  def start(port: Int, requestHandler: Handler[HttpServerRequest] = LoggingHandler, nullableName: String = null)(
      onConnect: PartialFunction[String, OnConnect])(implicit timeout: Duration, scheduler: Scheduler): ScalaVerticle = {
    val name             = Option(nullableName).getOrElse("general")
    val websocketHandler = RoutingSocketHandler(onConnect.andThen(ServerWebSocketHandler.replay(name)))
    start(HostPort.localhost(port), requestHandler, websocketHandler)
  }

  def start(hostPort: HostPort, requestHandler: Handler[HttpServerRequest])(socketByRoute: PartialFunction[String, Handler[ServerWebSocket]]): ScalaVerticle = {
    start(hostPort, requestHandler, RoutingSocketHandler(socketByRoute))
  }

  def start(hostPort: HostPort, requestHandler: Handler[HttpServerRequest], socketHandler: Handler[ServerWebSocket]): ScalaVerticle = {
    object Server extends ScalaVerticle {
      vertx = Vertx.vertx()

      override def start(): Unit = {
        vertx
          .createHttpServer()
          .requestHandler(requestHandler)
          .websocketHandler(socketHandler)
          .listen(hostPort.port, hostPort.host)
      }
    }
    Server.start()
    Server
  }

  def startRest(hostPort: HostPort, staticPath: Option[String])(implicit scheduler: Scheduler): Observable[RestRequestContext] = {
    val restHandler = RestHandler()
    object RestVerticle extends ScalaVerticle with StrictLogging {
      vertx = Vertx.vertx()

      val router = Router.router(vertx)

      val staticPathMsg = staticPath.fold("") { root =>
        val staticHandlerA = StaticHandler.create().setDirectoryListing(true).setAllowRootFileSystemAccess(true)
        val staticHandler = staticHandlerA.setWebRoot(root)
        router.route("/ui/*").handler(staticHandler)
        s"serving static data under $root"
      }
      router.route("/rest/*").handler(ctxt => restHandler.handle(ctxt.request()))

      logger.info(s"Starting REST server at $hostPort, serving static data under $staticPathMsg")

      override def start(): Unit = {
        vertx
          .createHttpServer()
          .requestHandler(router.accept) // restHandler.handle)
//          .requestHandler(restHandler.handle)
          .listen(hostPort.port, hostPort.host)
      }
    }
    RestVerticle.start()

    restHandler.requests
  }
}
