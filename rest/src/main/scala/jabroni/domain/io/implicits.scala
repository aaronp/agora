package jabroni.domain.io

// https://git.io/v99U9

import java.nio.charset.{Charset, StandardCharsets}
import java.nio.file._
import java.nio.file.attribute.FileAttribute

object implicits extends LowPriorityIOImplicits

trait LowPriorityIOImplicits {

  implicit class RichString(val path: String) {
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

    def text_=(str: String) = {
      createIfNotExists()
      setText(str)
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

    def mkDirs(atts: FileAttribute[_]*) = Files.createDirectories(path, atts: _*)

    def size = Files.size(path)

    def exists = Files.exists(path)

    def isDir = Files.isDirectory(path)

    def isFile = Files.isRegularFile(path)

    def children: Iterator[Path] = if (isDir) Files.list(path).iterator().asScala else Iterator.empty

    def delete(): Unit = {
      if (isDir) {
        children.foreach(_.delete())
        Files.delete(path)
      } else if (isFile) {
        Files.delete(path)
      }
    }
  }

}