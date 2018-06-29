package crud.api

import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

import agora.BaseIOSpec

abstract class BaseCrudApiSpec extends BaseIOSpec {

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
