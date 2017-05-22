package jabroni.rest

import java.nio.file.{Path, Paths}

import akka.actor.ActorSystem
import akka.http.scaladsl.testkit.RouteTestTimeout
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{Matchers, WordSpec}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.{implicitConversions, postfixOps}
import scala.util.Properties
import jabroni.domain.io.LowPriorityIOImplicits

class BaseSpec
  extends WordSpec
    with Matchers
    with FailFastCirceSupport
    with ScalaFutures
    with LowPriorityIOImplicits {

  /**
    * All the timeouts!
    */
  implicit def testTimeout: FiniteDuration = 10000.seconds

  implicit def default(implicit system: ActorSystem) = RouteTestTimeout(testTimeout)

  implicit override def patienceConfig =
    PatienceConfig(timeout = scaled(Span(testTimeout.toSeconds, Seconds)), interval = scaled(Span(150, Millis)))

  implicit def richFuture[T](fut: Future[T]) = new {
    def block = Await.result(fut, testTimeout)
  }

  def srcDir: Path = BaseSpec.srcDir
}
object BaseSpec extends LowPriorityIOImplicits {
  lazy val srcDir: Path = {
    def root(p: Path): Path = {
      if (p.fileName == "jabroni") p else {
        p.parent.map(root).getOrElse(sys.error("Hit file root looking for source root"))
      }
    }

    root(Properties.userDir.asPath).toAbsolutePath
  }
}
