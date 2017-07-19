package agora.exec

import java.nio.file.Path

import com.typesafe.config.Config
import agora.api.JobId
import agora.io.implicits._
import scala.util.{Properties, Try}

case class PathConfig(config: Config) {
  val appendJobId: Boolean = config.getBoolean("appendJobId")

  def mkDirs: Boolean = config.getBoolean("mkDirs")

  private def pathOpt = Try(config.getString("dir")).toOption.filterNot(_.isEmpty).map {
    case "sys-tmp"   => Properties.tmpDir
    case "user-home" => Properties.userHome
    case "."         => Properties.userDir
    case other       => other
  }

  lazy val path = pathOpt.map {
    case p if mkDirs => p.asPath.mkDirs()
    case p           => p.asPath
  }

  def dir(jobId: JobId): Option[Path] = path.map {
    case p if appendJobId => p.resolve(jobId).mkDir()
    case p                => p
  }
}
