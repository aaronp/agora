package agora.health

import java.lang.management.{ManagementFactory, MemoryMXBean}
import java.time.{LocalDateTime, ZoneOffset}

import io.circe.generic.auto.{exportDecoder, exportEncoder}
import io.circe.java8.time._

case class HealthDto(asOf: LocalDateTime, system: SystemDto, heapMemoryUsage: MemoryDto, nonHeapMemoryUsage: MemoryDto, objectPendingFinalizationCount: Int)

object HealthDto {
  private val memoryBean: MemoryMXBean = ManagementFactory.getMemoryMXBean

  implicit val encoder = exportEncoder[HealthDto].instance
  implicit val decoder = exportDecoder[HealthDto].instance

  def apply(): HealthDto = {
    new HealthDto(
      LocalDateTime.now(ZoneOffset.UTC),
      SystemDto(),
      MemoryDto(memoryBean.getHeapMemoryUsage),
      MemoryDto(memoryBean.getNonHeapMemoryUsage),
      memoryBean.getObjectPendingFinalizationCount
    )

  }
}
