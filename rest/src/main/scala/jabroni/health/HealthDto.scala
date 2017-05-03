package jabroni.health

import java.lang.management.{ManagementFactory, MemoryMXBean}

import io.circe.generic.auto.{exportDecoder, exportEncoder}
import io.circe.syntax._

case class HealthDto(heapMemoryUsage: MemoryDto, nonHeapMemoryUsage: MemoryDto, objectPendingFinalizationCount: Int)

object HealthDto {
  private val bean: MemoryMXBean = ManagementFactory.getMemoryMXBean

  implicit val encoder = exportEncoder[HealthDto].instance
  implicit val decoder = exportDecoder[HealthDto].instance

  def apply(): HealthDto = {
    new HealthDto(
      MemoryDto(bean.getHeapMemoryUsage),
      MemoryDto(bean.getNonHeapMemoryUsage),
      bean.getObjectPendingFinalizationCount)

  }
}
