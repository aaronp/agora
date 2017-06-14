package miniraft.state

import com.typesafe.scalalogging.StrictLogging

import scala.concurrent.Await
import scala.concurrent.duration.Duration

object RaftMain extends StrictLogging {
  def main(args: Array[String]): Unit = {
    val config = RaftConfig(args)

    var corpus = ""
    val systemFuture = RaftSystem[String](config) { entry =>
      corpus = corpus + entry.command
    }
    import config.serverImplicits.executionContext
    Await.result(systemFuture.flatMap(_.start(identity[String])), Duration.Inf)
  }

}
