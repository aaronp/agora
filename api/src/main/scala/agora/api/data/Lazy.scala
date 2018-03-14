package agora.api.data

import com.typesafe.scalalogging.LazyLogging

import scala.util.control.NonFatal

/**
  * A wrapper for lazy values which can be queried whether they are created
  *
  * @param mkValue
  * @tparam T
  */
class Lazy[T](mkValue: => T) extends AutoCloseable with LazyLogging {

  private var _created = false
  lazy val value = {
    _created = true
    mkValue
  }

  def created() = _created

  override def close(): Unit = if (created()) {
    value match {
      case auto: AutoCloseable =>
        try {
          auto.close()
        } catch {
          case NonFatal(e) => logger.error(s"${auto} threw on close: ${e.getMessage}", e)
        }
      case _ =>
    }
  }
}

object Lazy {
  def apply[T](value: => T): Lazy[T] = new Lazy(value)
}
