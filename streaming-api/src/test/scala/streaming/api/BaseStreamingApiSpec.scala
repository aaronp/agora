package streaming.api

import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

import agora.BaseIOSpec
import monix.execution.Scheduler
import org.scalatest.concurrent.Eventually

abstract class BaseStreamingApiSpec extends BaseIOSpec with Eventually {

  implicit def scheduler: Scheduler = monix.execution.Scheduler.Implicits.global

  val daemonicExecutor = java.util.concurrent.Executors.newFixedThreadPool(
    4,
    new ThreadFactory {
      val id = new AtomicInteger(0)

      override def newThread(r: Runnable): Thread = {
        val thread = new Thread(r)
        thread.setDaemon(true)
        thread.setName(s"${id.incrementAndGet()}-thread")
        thread
      }
    }
  )
}
