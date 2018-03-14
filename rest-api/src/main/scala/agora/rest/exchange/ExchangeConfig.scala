package agora.rest.exchange

import agora.api.exchange.observer.{ExchangeObserver, ExchangeObserverDelegate}
import agora.api.exchange.{Exchange, JobPredicate, ServerSideExchange}
import agora.api.worker.HostLocation
import agora.config._
import agora.rest._
import akka.http.scaladsl.HttpExt
import akka.stream.Materializer
import com.typesafe.config.{Config, ConfigFactory}
import org.reactivestreams.Subscription

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._

object ExchangeConfig {

  def apply(firstArg: String, theRest: String*): ExchangeConfig = apply(firstArg +: theRest.toArray)

  def apply(args: Array[String] = Array.empty, fallback: Config = ConfigFactory.empty): ExchangeConfig = {
    val ex = apply(configForArgs(args, fallback))
    ex.withFallback(load())
  }

  def apply(config: Config): ExchangeConfig = new ExchangeConfig(config)

  def load() = fromRoot(ConfigFactory.load())

  def fromRoot(config: Config) = apply(config.getConfig("agora.exchange").ensuring(!_.isEmpty))

}

class ExchangeConfig(c: Config) extends ServerConfig(c) {

  def withFallback(fallback: ExchangeConfig) = new ExchangeConfig(config.withFallback(fallback.config))

  def client: ExchangeRestClient = ExchangeRestClient(clientConfig.restClient)

  def connectObserver(obs: ExchangeObserver, location: HostLocation = clientConfig.location)(implicit clientSystem: AkkaImplicits): Future[Subscription] = {
    import clientSystem._
    ExchangeClientObserver.connectClient(location, obs, http)
  }

  def withQueueStateBufferTimeout = c.getDuration("withQueueStateBufferTimeout").toMillis.millis

  def newExchange(implicit obs: ExchangeObserverDelegate = ExchangeObserverDelegate(), matcher: JobPredicate = JobPredicate()): ServerSideExchange = {
    val underlying: Exchange                            = Exchange(obs)(matcher)
    val safeExchange: ActorExchange.ActorExchangeClient = ActorExchange(underlying, withQueueStateBufferTimeout, serverImplicits.system)
    new ServerSideExchange(safeExchange, obs)(serverImplicits.executionContext)
  }

}
