package agora.health

import java.lang.management.MemoryUsage

case class MemoryDto(init: Long, comitted: Long, max: Long, used: Long)
object MemoryDto {

  def apply(mu: MemoryUsage) = {

    new MemoryDto(
      init = mu.getInit,
      comitted = mu.getCommitted,
      max = mu.getMax,
      used = mu.getUsed
    )
  }

}
