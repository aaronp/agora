package agora.api

import java.nio.file.Path
import java.util.concurrent.atomic.AtomicLong

import agora.api.io.LowPriorityIOImplicits
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpec}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.language.{implicitConversions, postfixOps}
import scala.util.Properties

class BaseSpec extends WordSpec with Matchers with ScalaFutures with LowPriorityIOImplicits with BeforeAndAfterAll {

  /**
    * All the timeouts!
    */
  implicit def testTimeout: FiniteDuration = 10.seconds

  implicit override def patienceConfig =
    PatienceConfig(timeout = scaled(Span(testTimeout.toSeconds, Seconds)), interval = scaled(Span(150, Millis)))

  implicit def richFuture[T](fut: Future[T]) = new {
    def block = Await.result(fut, testTimeout)
  }

  def srcDir: Path = BaseSpec.srcDir

  private val dirCounter = new AtomicLong(System.currentTimeMillis())

  def withDir(f: Path => Unit) = {
    val dir: Path = s"target/test/${getClass.getSimpleName}-${dirCounter.incrementAndGet()}".asPath
    if (dir.exists) {
      dir.delete()
    }
    dir.mkDirs()
    try {
      f(dir)
    } finally {
      dir.delete()
    }
  }
}

object BaseSpec extends LowPriorityIOImplicits {
  lazy val srcDir: Path = {
    def root(p: Path): Path = {
      if (p.fileName == "agora") p
      else {
        p.parent.map(root).getOrElse(sys.error("Hit file root looking for source root"))
      }
    }

    root(Properties.userDir.asPath).toAbsolutePath
  }
}
