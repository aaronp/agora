package miniraft.state.rest

import agora.rest.client.{RestClient, RetryClient, RoundRobinClient}
import akka.http.scaladsl.model.{StatusCodes, Uri}
import com.typesafe.scalalogging.LazyLogging
import io.circe.{Decoder, Encoder}

import scala.concurrent.Future

/**
  * Exposes a REST client which client code can use to send requests of 'T' to the raft cluster
  *
  * @param client
  * @param newClient
  * @tparam T
  */
case class LeaderClient[T: Encoder : Decoder](client: RestClient, newClient: Uri => RestClient) extends LazyLogging {

  private val TempRedirectCode = StatusCodes.TemporaryRedirect.intValue

  private var currentLeader = client


  def append(value: T): Future[Boolean] = {

    doAppend(currentLeader, value)
  }

  private def doAppend(c: RestClient, value: T): Future[Boolean] = {
    implicit val ec = currentLeader.executionContext
    currentLeader.send(RaftHttp.leader.append(value)).flatMap { resp =>
      resp.status.intValue match {
        case TempRedirectCode =>
          val newLocationOpt = resp.headers.collectFirst {
            case header if header.name == "Location" => header.value
          }
          logger.debug(s"Request to ${c} redirected to leader at ${newLocationOpt}")

          def headerKeys = resp.headers.map(_.name).toList.sorted.distinct.mkString(",")

          val newLocation = newLocationOpt.getOrElse(sys.error(s"Encountered a redirect response w/o a 'Location' header: ${headerKeys}"))
          val redirectTo: Uri = Uri(newLocation)
          currentLeader = newClient(redirectTo)
          append(value)
        case _ =>
          if (!resp.status.isSuccess()) {
            val exp = new Exception(s"Append returned ${resp.status} : ${resp.entity}")
            logger.error(exp.getMessage)

            c match {
              case client: RoundRobinClient =>
                doAppend(client.reset(Option(exp)), value)
              case client: RetryClient =>
                doAppend(client.reset(Option(exp)), value)
              case _ =>
                Future.successful(resp.status.isSuccess())
            }
          } else {
            Future.successful(resp.status.isSuccess())

          }
      }
    }
  }
}
