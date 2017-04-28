package jabroni.rest

import akka.http.scaladsl.testkit.ScalatestRouteTest
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.{Matchers, WordSpec}

import scala.language.implicitConversions

class BaseSpec
  extends WordSpec
    with Matchers
    with ScalatestRouteTest
    with FailFastCirceSupport
    with ScalaFutures {

}
