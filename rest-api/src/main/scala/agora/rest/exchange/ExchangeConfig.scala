package agora.rest.exchange

import agora.api.exchange.{Exchange, JobPredicate, MatchObserver, ServerSideExchange}
import agora.config._
import agora.rest._
import com.typesafe.config.{Config, ConfigFactory}

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

  def newExchange(implicit obs: MatchObserver = MatchObserver(),
                  matcher: JobPredicate = JobPredicate()): ServerSideExchange = {
    val underlying: Exchange   = Exchange(obs)(matcher)
    val safeExchange: Exchange = ActorExchange(underlying, serverImplicits.system)
    new ServerSideExchange(safeExchange, obs)(serverImplicits.executionContext)
  }

}
