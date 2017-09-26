package agora.api.health

import java.lang.management.{ManagementFactory, OperatingSystemMXBean}

import com.sun
import com.typesafe.scalalogging.LazyLogging

import scala.util.control.NonFatal

case class SystemDto(architecture: String,
                     operatingSystem: String,
                     availableProcessors: Int,
                     committedVirtualMemorySize: Long,
                     freePhysicalMemorySize: Long,
                     freeSwapSpaceSize: Long,
                     processCpuLoad: Double,
                     systemLoadAverage: Double,
                     totalPhysicalMemorySize: Long,
                     totalSwapSpaceSize: Long)

object SystemDto extends LazyLogging {

  private lazy val defaultOsBean: OperatingSystemMXBean =
    ManagementFactory.getOperatingSystemMXBean
  private lazy val defaultSunOsBean: sun.management.OperatingSystemMXBean =
    ManagementFactory.getPlatformMXBean(classOf[com.sun.management.OperatingSystemMXBean])

  val empty = SystemDto("", "", 0, 0, 0, 0, 0, 0, 0, 0)

  def apply(): SystemDto = {
    val dto = withOsData(empty, defaultOsBean)
    try {
      withSunData(dto, defaultSunOsBean)
    } catch {
      case NonFatal(e) =>
        logger.error(s"Error producing system dto w/ sun data: $e", e)
        dto
    }
  }

  def withOsData(dto: SystemDto, osBean: OperatingSystemMXBean) = {
    dto.copy(
      architecture = osBean.getArch,
      operatingSystem = osBean.getName,
      systemLoadAverage = osBean.getSystemLoadAverage
    )
  }

  def withSunData(dto: SystemDto, sunOsBean: sun.management.OperatingSystemMXBean) = {
    dto.copy(
      availableProcessors = sunOsBean.getAvailableProcessors,
      committedVirtualMemorySize = sunOsBean.getCommittedVirtualMemorySize,
      freePhysicalMemorySize = sunOsBean.getFreePhysicalMemorySize,
      freeSwapSpaceSize = sunOsBean.getFreeSwapSpaceSize,
      processCpuLoad = sunOsBean.getProcessCpuLoad,
      totalPhysicalMemorySize = sunOsBean.getTotalPhysicalMemorySize,
      totalSwapSpaceSize = sunOsBean.getTotalSwapSpaceSize
    )
  }
}
