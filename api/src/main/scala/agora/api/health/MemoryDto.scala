package agora.api.health

import java.lang.management.MemoryUsage

case class MemoryDto(init: Long, committed: Long, max: Long, used: Long, usedPercent: Int) {
  def recalculatePercent = {
    if (max > 0) {
      val pcnt: Double = 100.0 * used / max
      copy(usedPercent = pcnt.toInt)
    } else {
      copy(usedPercent = 100)
    }
  }
}
object MemoryDto {

  def apply(mu: MemoryUsage) = {

    new MemoryDto(
      init = mu.getInit,
      committed = mu.getCommitted,
      max = mu.getMax,
      used = mu.getUsed,
      0
    ).recalculatePercent
  }

}
