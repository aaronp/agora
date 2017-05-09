package jabroni.rest

import akka.actor.ActorSystem
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}

import concurrent.duration._
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.{Await, Future}
import scala.language.implicitConversions

class BaseSpec
  extends WordSpec
    with Matchers
    with ScalatestRouteTest
    with FailFastCirceSupport
    with ScalaFutures {

  implicit def default(implicit system: ActorSystem) = RouteTestTimeout(30.seconds)

  implicit override val patienceConfig =
    PatienceConfig(timeout = scaled(Span(13, Seconds)), interval = scaled(Span(150, Millis)))

  implicit def richFuture[T](fut : Future[T]) = new {
    def block = Await.result(fut, 10.seconds)
  }

}
