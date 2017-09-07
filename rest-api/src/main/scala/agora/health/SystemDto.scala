package agora.health

import java.lang.management.{ManagementFactory, OperatingSystemMXBean}

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

object SystemDto {

  private val osBean: OperatingSystemMXBean = ManagementFactory.getOperatingSystemMXBean
  val sunOsBean                             = ManagementFactory.getPlatformMXBean(classOf[com.sun.management.OperatingSystemMXBean])

  def apply(): SystemDto = {
    new SystemDto(
      architecture = osBean.getArch,
      operatingSystem = osBean.getName,
      availableProcessors = sunOsBean.getAvailableProcessors,
      committedVirtualMemorySize = sunOsBean.getCommittedVirtualMemorySize,
      freePhysicalMemorySize = sunOsBean.getFreePhysicalMemorySize,
      freeSwapSpaceSize = sunOsBean.getFreeSwapSpaceSize,
      processCpuLoad = sunOsBean.getProcessCpuLoad,
      systemLoadAverage = osBean.getSystemLoadAverage,
      totalPhysicalMemorySize = sunOsBean.getTotalPhysicalMemorySize,
      totalSwapSpaceSize = sunOsBean.getTotalSwapSpaceSize
    )
  }
}
