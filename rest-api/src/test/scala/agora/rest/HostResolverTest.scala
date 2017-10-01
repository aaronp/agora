package agora.rest

import java.net.InetSocketAddress
import java.util.UUID

import agora.BaseSpec
import agora.api.worker.HostLocation
import com.typesafe.scalalogging.StrictLogging

import scala.sys.SystemProperties

class HostResolverTest extends BaseSpec with StrictLogging {
  "HostResolver" should {
    "resolve 'from-prop-x" in {
      val props = (new SystemProperties)
      val key   = UUID.randomUUID().toString
      val value = key.reverse
      props += (key -> value)
      try {

        val address: InetSocketAddress = InetSocketAddress.createUnresolved("localhost", 7777)
        val resolved                   = HostResolver(s"from-prop-${key}", HostLocation.localhost(1234)).resolveHostname(address)

        resolved shouldBe HostLocation(value, 1234)
      } finally {
        props -= key
      }
    }
  }
}
