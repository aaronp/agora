package agora.rest

import java.net.{InetAddress, InetSocketAddress}

import agora.api.worker.HostLocation
import com.typesafe.scalalogging.StrictLogging

import scala.util.Properties

/**
  * A means to resolve hostnames t
  */
trait HostResolver {

  /**
    * Resolve the given InetAddress to the hostname to use
    *
    * @param address the InetAddress obtained from a started server
    * @return the value to use for hostname addresses
    */
  def resolveHostname(address: InetSocketAddress): HostLocation
}

object HostResolver {

  val FromEnvR  = s"from-env-(.+)".r
  val FromPropR = s"from-prop-(.+)".r

  /**
    * one of:
    * 1) 'from-config-host' in which case it will use the 'host' value from this config
    * 2) 'from-inet-hostname', in which case it will use the [[InetAddress]] getHostName from this started server
    * 3) 'from-inet-hoststring', in which case it will use the [[InetSocketAddress]] getHostString from this started server
    * 4) 'from-inet-hostaddress', in which case it will use the [[InetAddress]] getHostAddress from this started server
    * 5) 'from-inet-canonical-hostname', in which case it will use the [[InetAddress]] getHostName from this started server
    * 6) 'from-env-XXX', where XXX is the system environment variable name
    * 7) 'from-prop-XXX', where XXX is the system property name
    * 8) the value verbatim from the configuration
    *
    * @return the resolve host name
    */
  class DefaultResolver(val configValue: String, val configLocation: HostLocation)
      extends HostResolver
      with StrictLogging {
    override def resolveHostname(socketAddress: InetSocketAddress) = {
      lazy val address = socketAddress.getAddress
      val hostName = configValue match {
        case "from-config-host"             => configLocation.host
        case "from-inet-hostname"           => address.getHostName
        case "from-inet-hoststring"         => socketAddress.getHostString
        case "from-inet-hostaddress"        => address.getHostAddress
        case "from-inet-canonical-hostname" => address.getCanonicalHostName
        case FromEnvR(envVar)               => sys.env.getOrElse(envVar, sys.error(s"cannot resolve host as env '${envVar}' not set"))
        case FromPropR(propVar) =>
          Properties
            .propOrNone(propVar)
            .getOrElse(sys.error(s"cannot resolve host as system property '${propVar}' not set"))
        case other => other
      }

      val location = HostLocation(hostName, configLocation.port)

      logger.debug(s"Given config '${configValue}' and conf loation ${configLocation}, resolved\n${HostResolver.debug(
        socketAddress)}\n to ${hostName}\n")

      location
    }
  }

  def debug(socketAddress: InetSocketAddress) = {
    val address = socketAddress.getAddress
    s"""
       |          inet-hostname : ${address.getHostName}
       |        inet-hoststring : ${socketAddress.getHostString}
       |inet-canonical-hostname : ${address.getCanonicalHostName}
       |       inet-hostaddress : ${address.getHostAddress}
       |                   port : ${socketAddress.getPort}
    """.stripMargin
  }

  def apply(configName: String, configLocation: HostLocation): HostResolver = {
    new DefaultResolver(configName, configLocation)
  }
}
