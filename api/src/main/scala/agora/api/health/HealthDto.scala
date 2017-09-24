package agora.api.health

import java.lang.management.{ManagementFactory, MemoryMXBean}
import java.time.{LocalDateTime, ZoneOffset}

import agora.api.exchange.{Exchange, UpdateSubscriptionAck}
import agora.api.worker.{SubscriptionKey, WorkerDetails}
import io.circe.generic.auto.{exportDecoder, exportEncoder}

import scala.concurrent.{ExecutionContext, Future}

case class HealthDto(asOf: LocalDateTime, system: SystemDto, heapMemoryUsage: MemoryDto, nonHeapMemoryUsage: MemoryDto, objectPendingFinalizationCount: Int) {

  def updateHealth(exchange: Exchange, key: SubscriptionKey)(implicit ec: ExecutionContext): Future[UpdateSubscriptionAck] = {
    HealthDto.updateHealth(exchange, key, this)
  }

}

object HealthDto extends io.circe.java8.time.TimeInstances {
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

  /**
    * Update the given subscription w/ the given [[HealthDto]]
    *
    * @return the update ack
    */
  def updateHealth(exchange: Exchange, key: SubscriptionKey, health: HealthDto = HealthDto())(implicit ec: ExecutionContext): Future[UpdateSubscriptionAck] = {
    exchange.updateSubscriptionDetails(key, WorkerDetails.empty.append("health", health))
  }
}
