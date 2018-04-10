package agora

import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{Executors, ThreadFactory}

import scala.concurrent.ExecutionContext

package object flow {

  def newContext() = {
    val factory = new ThreadFactory {
      private val id = new AtomicInteger(0)

      override def newThread(r: Runnable): Thread = {
        val t = new Thread(r)
        //t.setDaemon(true)
        t.setName(s"flow-thread-${id.incrementAndGet()}")
        t
      }
    }
    val es = Executors.newCachedThreadPool(factory)
    ExecutionContext.fromExecutorService(es)
  }
}
