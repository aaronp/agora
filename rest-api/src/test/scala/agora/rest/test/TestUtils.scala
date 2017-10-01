package agora.rest.test

import java.net.URL
import java.nio.file.{Path, Paths}

import agora.io.LowPriorityIOImplicits
import akka.stream.scaladsl.FileIO

object TestUtils extends LowPriorityIOImplicits {

  implicit class RichResource(val resource: String) extends AnyVal {

    def onClasspath: URL = {
      val url = getClass.getClassLoader.getResource(resource)
      require(url != null, s"Couldn't find $resource")
      url
    }

    def absolutePath: Path = Paths.get(onClasspath.toURI).toAbsolutePath

    def asSource = FileIO.fromPath(absolutePath)

    def executable: String = absolutePath.grantAllPermissions.toString
  }

}
