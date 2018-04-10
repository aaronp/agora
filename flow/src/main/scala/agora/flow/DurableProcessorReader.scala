package agora.flow

import scala.util.Try

trait DurableProcessorReader[T] {

  /** @param index the index from which to read
    * @return the value at a particular index (which may not exist or suffer some IO error)
    */
  def at(index: Long): Try[T]

}
