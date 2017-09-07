package miniraft.state.rest

import akka.http.scaladsl.model.{StatusCodes, Uri}
import com.typesafe.scalalogging.LazyLogging
import io.circe.{Decoder, Encoder}
import agora.rest.client.RestClient

import scala.concurrent.Future

/**
  * Exposes a REST client which client code can use to send requests of 'T' to the raft cluster
  *
  * @param client
  * @param newClient
  * @tparam T
  */
case class LeaderClient[T: Encoder: Decoder](client: RestClient, newClient: Uri => RestClient) extends LazyLogging {

  private val TempRedirectCode = StatusCodes.TemporaryRedirect.intValue

  private var currentLeader = client

  def append(value: T): Future[Boolean] = {
    val c = currentLeader
    import c.executionContext
    currentLeader.send(RaftHttp.leader.append(value)).flatMap { resp =>
      resp.status.intValue match {
        case TempRedirectCode =>
          val newLocationOpt = resp.headers.collectFirst {
            case header if header.name == "Location" => header.value
          }
          logger.debug(s"Request to ${c} redirected to leader at ${newLocationOpt}")

          def headerKeys = resp.headers.map(_.name).toList.sorted.distinct.mkString(",")

          val newLocation     = newLocationOpt.getOrElse(sys.error(s"Encountered a redirect response w/o a 'Location' header: ${headerKeys}"))
          val redirectTo: Uri = Uri(newLocation)
          currentLeader = newClient(redirectTo)
          append(value)
        case _ =>
          if (!resp.status.isSuccess()) {
            logger.error(s"Append returned ${resp.status} : ${resp.entity}")
          }

          Future.successful(resp.status.isSuccess())
      }
    }
  }
}
