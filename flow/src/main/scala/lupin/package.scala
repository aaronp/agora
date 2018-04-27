import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.{ExecutorService, Executors, ThreadFactory}

import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}

package object lupin {

  private val id = new AtomicInteger(0)

  val FlowPrefix = "flow-thread-"

  private def setName(prefix: String)(t: Thread) = {
    t.setName(s"$prefix${id.incrementAndGet()}")
    t
  }

  def newContextWithThreadPrefix(threadPrefix: String) = {
    newContext(setName(threadPrefix))
  }

  def newContext(init: Thread => Thread = setName(FlowPrefix)): ExecutionContextExecutorService = {
    val factory = new ThreadFactory {

      override def newThread(r: Runnable): Thread = {
        val t = new Thread(r)
        //t.setDaemon(true)
        t.setName(s"flow-thread-${id.incrementAndGet()}")
        t
      }
    }
    val es: ExecutorService = Executors.newCachedThreadPool(factory)
    ExecutionContext.fromExecutorService(es)
  }
}
