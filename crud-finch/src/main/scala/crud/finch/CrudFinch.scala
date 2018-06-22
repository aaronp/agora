package crud.finch

import java.util.concurrent.TimeUnit

import com.twitter.finagle.ListeningServer

import scala.io.StdIn

object CrudFinch extends App {

  import com.twitter.finagle.Http
  import io.finch._
  import io.finch.syntax._

  val api: Endpoint[String] = get("hello") {
    Ok("Hello, Finch World!")
  }

  val res: ListeningServer = Http.server.serve("0.0.0.0:8090", api.toServiceAs[Text.Plain])

  StdIn.readLine("Running on 8090, hit owt to stop")
  res.close().toJavaFuture.get(5, TimeUnit.SECONDS)
}
