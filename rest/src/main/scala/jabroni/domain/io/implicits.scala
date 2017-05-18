package jabroni.domain.io

// https://git.io/v99U9

import java.nio.charset.{Charset, StandardCharsets}
import java.nio.file._
import java.nio.file.attribute.{BasicFileAttributes, FileAttribute, PosixFilePermission}
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object implicits extends LowPriorityIOImplicits

trait LowPriorityIOImplicits {

  implicit class RichPathString(val path: String) {
    def asPath = Paths.get(path)
  }

  implicit class RichPath(val path: Path) {

    import scala.collection.JavaConverters._

    def setText(str: String, charset: Charset = StandardCharsets.UTF_8, options: Set[OpenOption] = Set(StandardOpenOption.CREATE, StandardOpenOption.WRITE)): Unit = {
      Files.write(path, str.getBytes(charset), options.toArray: _*)
    }

    def bytes = if (exists) Files.readAllBytes(path) else Array.empty[Byte]

    def getText(charset: Charset = StandardCharsets.UTF_8): String = new String(bytes, charset)

    def text: String = getText()

    def lines: Iterator[String] = Files.lines(path).iterator().asScala

    def text_=(str: String) = {
      createIfNotExists()
      setText(str)
      this
    }

    def parent = Option(path.getParent)

    def createIfNotExists(atts: FileAttribute[_]*): Path = {
      if (!exists) {
        mkParentDirs()
        Files.createFile(path, atts: _*)
      }
      path
    }

    def mkParentDirs(atts: FileAttribute[_]*) = parent.foreach(_.mkDirs(atts: _*))

    def mkDirs(atts: FileAttribute[_]*): Path = Files.createDirectories(path, atts: _*)

    def mkDir(atts: FileAttribute[_]*): Path = {
      if (!exists) Files.createDirectory(path, atts: _*) else path
    }

    def setFilePermissions(permission: PosixFilePermission, theRest: PosixFilePermission*): Path = {
      setFilePermissions(theRest.toSet + permission)
    }

    def setFilePermissions(permissions: Set[PosixFilePermission]): Path = {
      Files.setPosixFilePermissions(path, permissions.asJava)
    }

    def grantAllPermissions: Path = setFilePermissions(PosixFilePermission.values().toSet)

    def size = Files.size(path)

    def exists = Files.exists(path)

    def isDir = exists && Files.isDirectory(path)

    def isFile = exists && Files.isRegularFile(path)

    def children: Iterator[Path] = if (isDir) Files.list(path).iterator().asScala else Iterator.empty

    def attributes: BasicFileAttributes = Files.readAttributes(path, classOf[BasicFileAttributes])

    def created = attributes.creationTime.toInstant

    def createdUTC = created.atZone(ZoneId.of("UTC"))

    def createdString = DateTimeFormatter.ISO_ZONED_DATE_TIME.format(createdUTC)

    def fileName = path.getFileName.toString

    def delete(recursive: Boolean = true): Unit = {
      if (isDir && recursive) {
        children.foreach(_.delete())
        Files.delete(path)
      } else if (isFile) {
        Files.delete(path)
      }
    }
  }

}