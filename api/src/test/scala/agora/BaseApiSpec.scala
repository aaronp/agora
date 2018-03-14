package agora

import java.util.concurrent.ThreadFactory
import java.util.concurrent.atomic.AtomicInteger

/**
  * Adds json string context to the base spec:
  * {{{
  *   val foo : Json = json""" "foo" : "bar" """
  *
  * }}}
  */
abstract class BaseApiSpec extends BaseIOSpec with BaseJsonSpec {

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
