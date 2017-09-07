package agora.health

import java.lang.management.{ManagementFactory, MemoryMXBean}

import io.circe.generic.auto.{exportDecoder, exportEncoder}
import io.circe.syntax._

case class HealthDto(system: SystemDto, heapMemoryUsage: MemoryDto, nonHeapMemoryUsage: MemoryDto, objectPendingFinalizationCount: Int)

object HealthDto {
  private val memoryBean: MemoryMXBean = ManagementFactory.getMemoryMXBean

  implicit val encoder = exportEncoder[HealthDto].instance
  implicit val decoder = exportDecoder[HealthDto].instance

  def apply(): HealthDto = {
    new HealthDto(SystemDto(), MemoryDto(memoryBean.getHeapMemoryUsage), MemoryDto(memoryBean.getNonHeapMemoryUsage), memoryBean.getObjectPendingFinalizationCount)

  }
}
