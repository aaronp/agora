package agora.exec

import java.nio.file.Path

import agora.io.implicits._
import com.typesafe.config.Config

import scala.util.{Properties, Try}

case class PathConfig(config: Config) {

  def mkDirs: Boolean = config.getBoolean("mkDirs")

  def pathOpt: Option[Path] =
    Try(config.getString("dir")).toOption
      .filterNot(_.isEmpty)
      .map {
        case "sys-tmp"   => Properties.tmpDir
        case "user-home" => Properties.userHome
        case "."         => Properties.userDir
        case other       => other
      }
      .map {
        case p if mkDirs => p.asPath.mkDirs()
        case p           => p.asPath
      }

  lazy val path = pathOpt.getOrElse(sys.error("dir not set"))

}
