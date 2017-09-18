package agora.io.dao

import java.nio.file.Path
import agora.io.implicits._

trait Persist[T] {

  /**
    * Writes the value to the given file
    *
    * @param file  the file to save to
    * @param value the value to write
    */
  def write(file: Path, value: T): Unit
}

object Persist {

  def apply[T](f: (Path, T) => Unit): Persist[T] = new Persist[T] {
    override def write(file: Path, value: T): Unit = f(file, value)
  }

  implicit def writer[T: ToBytes] = new WriterInstance[T](implicitly[ToBytes[T]])

  def link[T](linkToThisFile: Path): Linker[T] = Linker(linkToThisFile)

  class WriterInstance[T](toBytes: ToBytes[T]) extends Persist[T] {
    override def write(file: Path, value: T): Unit = {
      file.bytes = toBytes.bytes(value)
    }
  }
  case class Linker[T](linkToThisFile: Path) extends Persist[T] {
    override def write(file: Path, value: T): Unit = {
      linkToThisFile.createLinkFrom(file)
    }
  }
}
