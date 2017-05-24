package jabroni.rest.test

import java.net.URL
import java.nio.file.{Path, Paths}
import java.time.LocalDateTime
import java.time.format._

import akka.actor.ActorSystem
import akka.stream.scaladsl.FileIO
import akka.stream.{ActorMaterializer, Materializer}
import jabroni.domain.io.LowPriorityIOImplicits

import scala.util.Try

object TestUtils extends LowPriorityIOImplicits {

  implicit class RichResource(val resource: String) extends AnyVal {

    def onClasspath: URL = {
      val url = getClass.getClassLoader.getResource(resource)
      require(url != null, s"Couldn't find $resource")
      url
    }

    def absolutePath: Path = Paths.get(onClasspath.toURI).toAbsolutePath

    def asSource = FileIO.fromPath(absolutePath)

    def executable = absolutePath.grantAllPermissions.toString
  }

  private def timestamp = {
    DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(LocalDateTime.now())
  }

  def withMaterializer[T](implicit f: Materializer => T): T = {
    implicit val sys = ActorSystem()
    implicit val mat = ActorMaterializer()
    try {
      f(mat)
    } finally {
      Try(mat.shutdown())
      Try(sys.terminate())
    }
  }

  def withTmpFile[T](name: String)(f: Path => T) = {
    val tmpFile = s"target/tmp/$name-${timestamp}.test".asPath.createIfNotExists()
    try {
      f(tmpFile)
    } finally {
      tmpFile.delete()
    }
  }

  def withTmpDir[T](name: String)(f: Path => T) = {
    val targetDir = s"target/tmp/${timestamp}".asPath.mkDirs()
    require(targetDir.exists, s"$targetDir wasn't created")
    require(targetDir.isDir, s"$targetDir isn't a dir")
    try {
      f(targetDir)
    } finally {
      targetDir.delete()
    }
  }
}
