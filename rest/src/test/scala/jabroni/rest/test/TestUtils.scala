package jabroni.rest.test

import java.nio.file.{Files, Path, Paths}
import java.time.LocalDateTime
import java.time.format._

import akka.actor.ActorSystem
import akka.stream.{ActorMaterializer, Materializer}

import scala.util.Try

object TestUtils {

  import jabroni.domain.io.implicits._

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
