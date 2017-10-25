package agora.rest.worker

import agora.api.exchange.WorkSubscription
import agora.api.json.JPredicate
import agora.api.worker.{HostLocation, WorkerDetails}
import com.typesafe.config.{Config, ConfigRenderOptions}
import io.circe
import agora.config.implicits._
import io.circe.Json

import scala.util.Try

/**
  * A parsed work subscription
  *
  * @param subscriptionConfig
  */
case class SubscriptionConfig(subscriptionConfig: Config) {

  import SubscriptionConfig._

  /** @param location the location of the worker
    * @return a WorkSubscription based on the configuration
    */
  def subscription(location: HostLocation): WorkSubscription = subscription(workerDetails(location))

  /** @param details the details of the worker
    * @return a WorkSubscription based on the configuration
    */
  def subscription(details: WorkerDetails): WorkSubscription = subscriptionEither(details) match {
    case Left(err) => sys.error(s"Couldn't parse the config as a subscription: $err")
    case Right(s)  => s
  }

  /**
    * Creates the worker details using the 'resolvedHostPort' from the subscription info (if non-empty)
    * otherwise the provided default location
    * @param defaultLocation the location to use if the 'resolvedHostPort' is not set
    * @return the WorkerDetails
    */
  def workerDetails(defaultLocation: HostLocation): WorkerDetails = {
    val detailsConf = subscriptionConfig.getConfig("details")
    val name        = detailsConf.getString("name")
    val id          = detailsConf.getString("id")
    val path        = detailsConf.getString("path")
    val runUser     = detailsConf.getString("runUser")
    val resolvedHostPort = if (detailsConf.hasPath("resolvedHostPort")) {
      detailsConf.getString("resolvedHostPort")
    } else {
      ""
    }

    val resolvedLocation = resolvedHostPort match {
      case HostLocation(loc) if loc.host.nonEmpty => loc
      case _                                      => defaultLocation
    }
    WorkerDetails(resolvedLocation, path, name, id, runUser).append(asJson(detailsConf.withoutPath("resolvedHostPort")))
  }

  def asMatcher(at: String): Either[circe.Error, JPredicate] = {
    val fromConfig: Option[Either[circe.Error, JPredicate]] = Try(subscriptionConfig.getConfig(at)).toOption.map { subConf =>
      val json = asJson(subConf)
      json.as[JPredicate]
    }

    val fromString = asJson(subscriptionConfig).hcursor.downField(at).as[JPredicate]

    fromConfig.getOrElse(fromString)
  }

  private def subscriptionEither(details: WorkerDetails, prefix: String = ""): Either[circe.Error, WorkSubscription] = {

    for {
      jm <- asMatcher("jobCriteria").right
      sm <- asMatcher("submissionCriteria").right
    } yield {
      WorkSubscription.forDetails(details, jm, sm, subscriptionReferences)
    }
  }

  def subscriptionReferences = subscriptionConfig.asList("subscriptionReferences").toSet

}

object SubscriptionConfig {

  def asJson(c: Config): Json = {
    val json = c.root.render(ConfigRenderOptions.concise().setJson(true))
    _root_.io.circe.parser.parse(json).right.get
  }
}
