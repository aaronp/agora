package crud.tyrus

import java.net.URI

import javax.websocket._

import scala.io.StdIn

object CrudWSClient extends App {

  //https://stackoverflow.com/questions/26452903/javax-websocket-client-simple-example

  object MyEndpoint extends javax.websocket.Endpoint {

    override def onClose(session: Session, closeReason: CloseReason) = {

      println(s"on close $session w/ $closeReason")
    }

    override def onError(session: Session, thr: Throwable): Unit = {

      println(s"on error $session w/ $thr")
    }

    override def onOpen(session: Session, config: EndpointConfig): Unit = {

      println(s"Opening w/ $session and $config")

      session.addMessageHandler(new MessageHandler.Whole[String] {
        override def onMessage(message: String): Unit = {
          println("got: " + message)
        }

      })
    }
  }

//  val x = org.glassfish.tyrus.container.grizzly.GrizzlyEngine
  val container   = ContainerProvider.getWebSocketContainer()
  val endpointURI = new URI("wss://real.okcoin.cn:10440/websocket/okcoinapi")
  val x: Session  = container.connectToServer(MyEndpoint, endpointURI)

  StdIn.readLine("hit owt to close")

}
