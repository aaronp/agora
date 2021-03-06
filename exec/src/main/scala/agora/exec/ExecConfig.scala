package agora.exec

import java.util.concurrent.TimeUnit

import agora.api.exchange.WorkSubscription
import agora.api.worker.HostLocation
import agora.config.configForArgs
import agora.exec.client.{ExecutionClient, ProcessRunner, QueryClient, RemoteRunner}
import agora.exec.events.{HousekeepingConfig, SystemEventMonitor}
import agora.exec.rest.{ExecutionRoutes, QueryRoutes, UploadRoutes}
import agora.exec.workspace.WorkspaceClient
import agora.rest.RunningService
import agora.rest.worker.{SubscriptionConfig, SubscriptionGroup, WorkerConfig}
import akka.stream.Materializer
import com.typesafe.config.{Config, ConfigFactory}

import scala.concurrent.Future
import scala.concurrent.duration._

/**
  * Represents a worker configuration which can execute requests
  *
  * @param execConfig
  */
class ExecConfig(execConfig: Config) extends WorkerConfig(execConfig) with ExecApiConfig with Serializable {

  /** Convenience method for starting an exec service.
    * Though this may seem oddly placed on a configuration, a service and its configuration
    * are tightly coupled, so whether you do :
    * {{{
    *   Service(config)
    * }}}
    * or
    * {{{
    *   config.newService
    * }}}
    * either should be arbitrary. And by doing it this way, hopefully the readability will be better from
    * the point of
    *
    * {{{
    * userArgs => config => started/running service
    * }}}
    *
    * @return a Future of a [[RunningService]]
    */
  def start(): Future[RunningService[ExecConfig, ExecBoot]] = ExecBoot(this).start()

  def execSubscriptions(resolvedLocation: HostLocation): SubscriptionGroup = {
    import scala.collection.JavaConverters._
    val subscriptionList = execConfig.getConfigList("execSubscriptions").asScala.map { conf =>
      SubscriptionConfig(conf).subscription(resolvedLocation)
    }
    SubscriptionGroup(subscriptionList.toList, initialRequest)
  }

  /** @param resolvedLocation the resolve host location
    * @return a work subscription based on the resolved host
    */
  def runSubscription(resolvedLocation: HostLocation): WorkSubscription =
    SubscriptionConfig(config.getConfig("runSubscription")).subscription(resolvedLocation)

  override def withFallback(fallback: Config): ExecConfig = new ExecConfig(config.withFallback(fallback))

  override def withOverrides(overrides: Config): ExecConfig = new ExecConfig(overrides.withFallback(execConfig))

  override protected def swaggerApiClasses: Set[Class[_]] = {
    super.swaggerApiClasses + classOf[ExecutionRoutes] + classOf[UploadRoutes] + classOf[QueryRoutes]
  }

  def defaultEnv: Map[String, String] = cachedDefaultEnv

  private lazy val cachedDefaultEnv = {
    val runnerEnvConf: Map[String, String] = execConfig.getConfig("runnerEnv").collectAsMap

    val hostKeys: List[String] = execConfig.asList("runnerEnvFromHost")

    val fromHost: List[(String, String)] = hostKeys.flatMap { key =>
      agora.config.propOrEnv(key).map { value =>
        key -> value
      }
    }
    runnerEnvConf ++ fromHost
  }

  def workspacesConfig = execConfig.getConfig("workspaces")

  lazy val workspacesPathConfig: PathConfig = PathConfig(workspacesConfig.ensuring(!_.isEmpty))

  def uploadsDir =
    workspacesPathConfig.pathOpt.getOrElse(sys.error("Invalid configuration - no uploads directory set"))

  def errorLimit = Option(execConfig.getInt("errorLimit")).filter(_ > 0)

  def healthUpdateFrequency = execConfig.getDuration("healthUpdateFrequency", TimeUnit.MILLISECONDS).millis

  /** @return a client which will execute commands via the [[agora.api.exchange.Exchange]]
    */
  def remoteRunner(implicit mat: Materializer = serverImplicits.materializer): RemoteRunner = {
    ProcessRunner(exchangeClient, clientConfig.submissionDetails, this)
  }

  /** @return a client directly to the [[ExecutionRoutes]]
    */
  def executionClient(): ExecutionClient = {
    ExecutionClient(clientConfig.restClient, defaultFrameLength)
  }

  /** @return a client for the [[QueryRoutes]]
    */
  def queryClient(): QueryClient = QueryClient(clientConfig.restClient)

  def workspaceClient: WorkspaceClient = defaultWorkspaceClient

  private lazy val defaultWorkspaceClient: WorkspaceClient = {
    val pollFreq               = workspacesConfig.getDuration("bytesReadyPollFrequency").toMillis.millis
    val workspaceDirProperties = WorkspaceClient.workspaceDirAttributes(workspacesConfig.getString("workspaceDirAttributes"))
    WorkspaceClient(uploadsDir, serverImplicits.system, pollFreq, Set(workspaceDirProperties))
  }

  def eventMonitorConfig: EventMonitorConfig = defaultEventMonitor

  def eventMonitor: SystemEventMonitor = eventMonitorConfig.eventMonitor

  def enableCache: Boolean = execConfig.getBoolean("enableCache")

  def housekeeping = HousekeepingConfig(execConfig.getConfig("housekeeping"))

  private lazy val defaultEventMonitor = new EventMonitorConfig(execConfig.getConfig("eventMonitor"))(serverImplicits)

  override def toString = execConfig.root.render()
}

object ExecConfig {

  def apply(firstArg: String, theRest: String*): ExecConfig = apply(firstArg +: theRest.toArray)

  def apply(args: Array[String] = Array.empty, fallbackConfig: Config = ConfigFactory.empty): ExecConfig = {
    val ec: ExecConfig = apply(configForArgs(args, fallbackConfig))
    ec.withFallback(load().config)
  }

  def apply(config: Config): ExecConfig = new ExecConfig(config)

  def unapply(config: ExecConfig) = Option(config.config)

  def load() = fromRoot(ConfigFactory.load())

  def fromRoot(config: Config): ExecConfig = apply(config.getConfig("exec").ensuring(!_.isEmpty))

}
