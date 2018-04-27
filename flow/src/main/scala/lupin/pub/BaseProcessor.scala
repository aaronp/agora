package lupin.pub

import lupin.sub.BaseSubscriber
import org.reactivestreams.{Processor, Publisher}

/**
  * BaseProcessor an abstract, base implementation of a processor
  *
  * @tparam T the processor type
  */
trait BaseProcessor[T] extends Publisher[T] with BaseSubscriber[T] with Processor[T, T]