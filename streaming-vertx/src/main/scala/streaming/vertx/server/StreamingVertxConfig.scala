package streaming.vertx.server
import com.typesafe.config.{Config, ConfigFactory}
import monix.execution.Scheduler
import monix.execution.schedulers.SchedulerService
import streaming.api.HostPort

case class StreamingVertxConfig(config: Config) {
  val hostPort: HostPort         = HostPort(config.getString("host"), config.getInt("port"))
  val staticPath: Option[String] = Option(config.getString("staticPath")).filterNot(_.isEmpty)

  def summary() = {
    import agora.config.implicits._
    config.summary().mkString("\n")
  }

  lazy val computeScheduler: SchedulerService = Scheduler.computation()
  lazy val ioScheduler: SchedulerService      = Scheduler.io()
}

object StreamingVertxConfig {

  def fromArgs(a: Array[String]) = {
    val config: Config = agora.config.configForArgs(a, ConfigFactory.load().getConfig("streaming-vertx")).resolve()

    StreamingVertxConfig(config)
  }
}
