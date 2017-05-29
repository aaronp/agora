package miniraft.state

import com.typesafe.config.Config

import scala.concurrent.duration._

case class RaftConfig(config: Config) {

  object heartbeat {
    private val hb = config.getConfig("heartbeat")
    val min: Duration = hb.getDuration("min").toMillis.millis
    val max = hb.getDuration("max").toMillis.millis
  }


  object election {
    private val hb = config.getConfig("election")
    val min: Duration = hb.getDuration("min").toMillis.millis
    val max = hb.getDuration("max").toMillis.millis
  }


}
