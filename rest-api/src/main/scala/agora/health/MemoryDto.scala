package agora.health

import java.lang.management.MemoryUsage

case class MemoryDto(init: Long, committed: Long, max: Long, used: Long)
object MemoryDto {

  def apply(mu: MemoryUsage) = {

    new MemoryDto(
      init = mu.getInit,
      committed = mu.getCommitted,
      max = mu.getMax,
      used = mu.getUsed
    )
  }

}
