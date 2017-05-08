package jabroni.rest

import akka.actor.ActorSystem
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}

import concurrent.duration._
import org.scalatest.{Matchers, WordSpec}

import concurrent.{Await, Future}
import language.implicitConversions
import language.postfixOps

class BaseSpec
  extends WordSpec
    with Matchers
    with FailFastCirceSupport
    with ScalaFutures {

  /**
    * All the timeouts!
    */

  implicit def testTimeout: FiniteDuration = 10.seconds

  implicit def default(implicit system: ActorSystem) = RouteTestTimeout(testTimeout)

  implicit override def patienceConfig =
    PatienceConfig(timeout = scaled(Span(testTimeout.toSeconds, Seconds)), interval = scaled(Span(150, Millis)))

  implicit def richFuture[T](fut: Future[T]) = new {
    def block = Await.result(fut, testTimeout)
  }

}
